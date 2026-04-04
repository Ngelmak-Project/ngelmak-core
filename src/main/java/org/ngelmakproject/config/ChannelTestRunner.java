package org.ngelmakproject.config;

import org.ngelmakproject.repository.ChannelRepository;
import org.ngelmakproject.service.ChannelService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ChannelTestRunner implements CommandLineRunner {

    private final ChannelRepository channelRepository;
    private final ChannelService channelService;

    public ChannelTestRunner(ChannelRepository channelRepository, ChannelService channelService) {
        this.channelRepository = channelRepository;
        this.channelService = channelService;
    }

    @Override
    public void run(String... args) {
        System.out.println("=== Channel Test Runner Started ===");
        channelRepository.findAll().stream()
            .filter(channel -> channel.getIdentifier() != null)
            .map(e -> {
                e.setIdentifier(channelService.generateUniqueIdentifier(e.getName()));
                return channelRepository.save(e);
            })
            .peek(e -> {
                System.out.println("Updated Channel: " + e.getName() + " with Identifier: " + e.getIdentifier());
            })
            .collect(java.util.stream.Collectors.toList());

    }
}

