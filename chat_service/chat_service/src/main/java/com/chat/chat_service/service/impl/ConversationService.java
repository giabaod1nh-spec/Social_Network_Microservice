package com.chat.chat_service.service.impl;

import com.chat.chat_service.dto.request.ConversationRequest;
import com.chat.chat_service.dto.response.ConversationResponse;
import com.chat.chat_service.entity.Conversation;
import com.chat.chat_service.entity.ParticipantInfo;
import com.chat.chat_service.exception.AppException;
import com.chat.chat_service.exception.ErrorCode;
import com.chat.chat_service.mapper.ConversationMapper;
import com.chat.chat_service.repository.ConservationRepository;
import com.chat.chat_service.repository.httpclient.ProfileClient;
import com.chat.chat_service.service.IConversationService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CHAT_SERVICE")
public class ConversationService implements IConversationService {
    private final ProfileClient profileClient;
    private final ConservationRepository conservationRepository;
    private final ConversationMapper conversationMapper;
    @Override
    public ConversationResponse createConversation(ConversationRequest request) {
        // Fetch user infos
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        var userInfoResponse = profileClient.getUserProfileById(userId);

        var participantInfoResponse = profileClient.getUserProfileById(
                request.getParticipantIds().getFirst());


        if (Objects.isNull(userInfoResponse) || Objects.isNull(participantInfoResponse)) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        var userInfo = userInfoResponse.getResult();
        log.info("user dang active: {}{}", userInfo.getUserName(), userInfo.getAvatar());

        var participantInfo = participantInfoResponse.getResult();
        log.info("user muon nhan den: {}{}", participantInfo.getUserName(), participantInfo.getAvatar());


        List<String> userIds = new ArrayList<>();
        userIds.add(userId);
        userIds.add(participantInfo.getUserId());

        var sortedIds = userIds.stream().sorted().toList();
        String userIdHash = generateParticipantHash(sortedIds);

        List<ParticipantInfo> participantInfos = List.of(
                ParticipantInfo.builder()
                        .userId(userInfo.getUserId())
                        .userName(userInfo.getUserName())
                        //.firstName(userInfo.getFirstName())
                        //.lastName(userInfo.getLastName())
                        .avatar(userInfo.getAvatar())
                        .build(),
                ParticipantInfo.builder()
                        .userId(participantInfo.getUserId())
                        .userName(participantInfo.getUserName())
                        //.firstName(participantInfo.getFirstName())
                        //.lastName(participantInfo.getLastName())
                        .avatar(participantInfo.getAvatar())
                        .build()
        );

        // Build conversation info
        Conversation conversation = Conversation.builder()
                .type(request.getType())
                .participantHash(userIdHash)
                .createdDate(Instant.now())
                .modifiedDate(Instant.now())
                .participants(participantInfos)
                .build();

        conversation = conservationRepository.save(conversation);

        return toConversationResponse(conversation);
    }

    @Override
    public List<ConversationResponse> myConversation() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Conversation> conversations = conservationRepository.findAllByParticipantIdsContains(userId);

        return conversations.stream().map(this::toConversationResponse).toList();
    }


    private String generateParticipantHash(List<String> ids) {
        StringJoiner stringJoiner = new StringJoiner("_");
        ids.forEach(stringJoiner::add);

        // SHA 256

        return stringJoiner.toString();
    }

    private ConversationResponse toConversationResponse(Conversation conversation) {
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();

        ConversationResponse conversationResponse = conversationMapper.convertConversationToResponse(conversation);

        conversation.getParticipants().stream()
                .filter(participantInfo -> !participantInfo.getUserId().equals(currentUserId))
                .findFirst().ifPresent(participantInfo -> {
                    conversationResponse.setConversationName(participantInfo.getUserName());
                    conversationResponse.setConversationAvatar(participantInfo.getAvatar());
                });

        return conversationResponse;
    }
}
