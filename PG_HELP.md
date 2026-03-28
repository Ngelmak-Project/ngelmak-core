# PostgreSQL Configuration, Indexing, and Full‑Text Search

This document consolidates PostgreSQL installation notes, indexing strategy, feed‑query optimization, and full‑text search setup. It is intended as a reference for configuring and maintaining the database layer of the application.

---

## 1. Installation and Configuration

### 1.1 PostgreSQL Installation (Ubuntu)

```bash
sudo apt -y install postgresql-14
sudo systemctl restart postgresql
```

### 1.2 Remote Access Configuration

`postgresql.conf`:

```
listen_addresses = '*'
```

`pg_hba.conf`:

```bash
sudo sed -i '/^host/s/ident/md5/' /etc/postgresql/14/main/pg_hba.conf
sudo sed -i '/^local/s/peer/trust/' /etc/postgresql/14/main/pg_hba.conf
echo "host all all 0.0.0.0/0 md5" | sudo tee -a /etc/postgresql/14/main/pg_hba.conf
sudo systemctl restart postgresql
```

### 1.3 Basic Administration

```sql
ALTER USER postgres PASSWORD '<password>';
CREATE USER <username> WITH PASSWORD '<password>';
CREATE DATABASE <database_name> OWNER <username>;
```

---

## 2. Feed Query Optimization

Feed retrieval relies on timestamp‑based filtering. PostgreSQL must be able to efficiently locate recent rows and avoid scanning older data.

### 2.1 Timestamp Index

```sql
CREATE INDEX idx_nk_post_at ON nk_post (at DESC);
```

This index supports:

- Range filtering on `at >= :windowStart`
- Ordering by recency
- Early termination when combined with `LIMIT`

### 2.2 Composite Index (Optional)

```sql
CREATE INDEX idx_nk_post_at_comment
ON nk_post (at DESC, comment_count DESC);
```

Useful when scoring incorporates both recency and engagement metrics.

### 2.3 Partial Index (Optional)

```sql
CREATE INDEX idx_nk_post_recent
ON nk_post (at DESC)
WHERE at >= NOW() - INTERVAL '30 days';
```

Reduces index size when the feed window is restricted to recent posts.

### 2.4 Query Plan Characteristics

A correctly optimized feed query typically shows:

- `Index Scan Backward` on `idx_nk_post_at`
- Low heap block reads
- No sequential scan
- Fast termination due to `LIMIT`

Example:

```
Index Scan Backward using idx_nk_post_at on nk_post
  Filter: (at >= $1)
  Rows Removed by Filter: ...
```

---

## 3. Full‑Text Search

Full‑text search is implemented using PostgreSQL’s built‑in text search system.  
The configuration includes:

- A generated `tsvector` column
- A GIN index
- A search query returning only post IDs
- Hydration performed in the application layer

### 3.1 Search Vector Column

```sql
ALTER TABLE nk_post
ADD COLUMN textsearchable_index_col tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('french', title), 'A') ||
    setweight(to_tsvector('french', content), 'B') ||
    setweight(to_tsvector('french', coalesce(keywords, '')), 'D')
) STORED;
```

### 3.2 GIN Index

```sql
CREATE INDEX nk_post_textsearch_idx
ON nk_post USING GIN (textsearchable_index_col);
```

### 3.3 Query Behavior

Queries using:

```sql
textsearchable_index_col @@ websearch_to_tsquery('french', :fullText)
```

produce:

- `Bitmap Index Scan` on `nk_post_textsearch_idx`
- `Bitmap Heap Scan` on matching rows
- Ranking via `ts_rank_cd`

Example plan excerpt:

```
Bitmap Index Scan on nk_post_textsearch_idx
  Index Cond: (textsearchable_index_col @@ query)
Bitmap Heap Scan
  Recheck Cond: ...
```

---

## 4. Full‑Text Search Query (ID‑Only)

The search engine retrieves only post IDs from PostgreSQL.  
Hydration, channel resolution, and reaction aggregation occur in the application layer.

### 4.1 SQL

```sql
SELECT p.id
FROM nk_post p, websearch_to_tsquery('french', :fullText) query
WHERE p.status = 'VALIDATED'
  AND p.textsearchable_index_col @@ query
ORDER BY ts_rank_cd(p.textsearchable_index_col, query) DESC
LIMIT :limit OFFSET :offset;
```

This structure ensures:

- Index‑only filtering
- Efficient ranking
- Predictable pagination
- Separation of search and hydration logic

---

## 5. JPA Integration

### 5.1 Repository

```java
public interface PostSearchRepository {

    @Query(
        value = """
            SELECT p.id
            FROM nk_post p, websearch_to_tsquery('french', :fullText) query
            WHERE p.status = 'VALIDATED'
              AND p.textsearchable_index_col @@ query
            ORDER BY ts_rank_cd(p.textsearchable_index_col, query) DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true
    )
    List<Long> searchIds(
        @Param("fullText") String fullText,
        @Param("limit") int limit,
        @Param("offset") int offset
    );
}
```

### 5.2 Service Layer

```java
@Transactional(readOnly = true)
public PageDTO<PostDTO> fullTextSearch(String fullText, Pageable pageable) {

    List<Long> ids = postSearchRepository.searchIds(
        fullText,
        pageable.getPageSize(),
        (int) pageable.getOffset()
    );

    if (ids.isEmpty()) {
        return PageDTO.empty(pageable);
    }

    List<Post> posts = postRepository.findAllByIdIn(ids);

    List<Reaction> reactions = reactionRepository.findByPostIds(ids);
    Map<Long, List<Reaction>> reactionsByPost =
            ReactionService.groupReactionsByPost(reactions);

    List<PostDTO> dtos = posts.stream()
        .map(post -> {
            List<Reaction> postReactions =
                reactionsByPost.getOrDefault(post.getId(), List.of());
            ReactionSummaryDTO summary =
                ReactionSummaryDTO.from(postReactions, null);
            return PostDTO.from(post, summary);
        })
        .toList();

    return PageDTO.from(new PageImpl<>(dtos, pageable, dtos.size()));
}
```

---

## 6. Query Planning Notes

### 6.1 Bitmap Index Scan  
Used when many rows match the search condition.  
Combines index results efficiently before accessing heap pages.

### 6.2 Index Scan  
Used when the planner estimates a small number of matching rows.  
Traverses index entries directly.

### 6.3 Sequential Scan  
Occurs when:

- No matching index exists  
- The planner estimates low selectivity  
- The table is small  

Avoided by ensuring:

- Matching operator classes (`@@` for GIN)  
- Accurate statistics (`ANALYZE`)  
- Correct index predicates  