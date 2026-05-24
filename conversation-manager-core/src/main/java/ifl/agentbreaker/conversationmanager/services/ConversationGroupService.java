package ifl.agentbreaker.conversationmanager.services;

import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.conversationmanager.dao.ConversationGroupMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationGroupRelationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.AddConversationToGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.CreateConversationGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.DeleteConversationGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.RemoveConversationFromGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ReorderConversationGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.UpdateConversationGroupAbstractRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationGroupAbstract;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGroup;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGroupRelation;
import ifl.agentbreaker.conversationmanager.support.BusinessIdManager;
import ifl.agentbreaker.conversationmanager.support.TextNormalizer;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import stark.dataworks.boot.autoconfig.web.LogArgumentsAndResponse;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.List;

@Slf4j
@Service
@Validated
@LogArgumentsAndResponse
public class ConversationGroupService
{
    private static final int ERROR_GROUP_NOT_FOUND = 2102;
    private static final int ERROR_INVALID_CONVERSATION = 2103;
    private static final int MAX_GROUP_NAME_LENGTH = 100;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationGroupMapper conversationGroupMapper;

    @Autowired
    private ConversationGroupRelationMapper conversationGroupRelationMapper;

    /**
     * Create a new conversation group, by default, the sort is (current max sort order + 1).
     * @param request The group creation request.
     * @return The created group.
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationGroupAbstract> createConversationGroup(@Valid CreateConversationGroupRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        ConversationGroup group = new ConversationGroup();
        group.setCreatorId(userId);
        group.setModifierId(userId);
        group.setGroupId(BusinessIdManager.newConversationGroupId());
        group.setName(TextNormalizer.trimToMaxLength(request.getName(), MAX_GROUP_NAME_LENGTH));
        group.setDescription(TextNormalizer.trimToNull(request.getDescription()));
        group.setSortOrder(conversationGroupMapper.getMaxConversationGroupSortOrder(userId) + 1);
        conversationGroupMapper.insertConversationGroup(group);

        return ServiceResponse.buildSuccessResponse(toConversationGroupAbstract(group));
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationGroupAbstract> updateConversationGroupAbstract(@Valid UpdateConversationGroupAbstractRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        ConversationGroup group = conversationGroupMapper.getConversationGroupByIdForUser(request.getGroupId(), userId);
        if (group == null)
            return ServiceResponse.buildErrorResponse(ERROR_GROUP_NOT_FOUND, "Conversation group does not exist.");

        if (StringUtils.hasText(request.getName()))
            group.setName(TextNormalizer.trimToMaxLength(request.getName(), MAX_GROUP_NAME_LENGTH));

        if (request.getDescription() != null)
            group.setDescription(TextNormalizer.trimToNull(request.getDescription()));

        group.setModifierId(userId);
        int updated = conversationGroupMapper.updateConversationGroupAbstract(group);

        if (updated <= 0)
            return ServiceResponse.buildErrorResponse(ERROR_GROUP_NOT_FOUND, "Conversation group does not exist.");

        return ServiceResponse.buildSuccessResponse(toConversationGroupAbstract(group));
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<List<ConversationGroupAbstract>> reorderConversationGroup(@Valid ReorderConversationGroupRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        if (!CollectionUtils.isEmpty(request.getConversationGroupAbstracts()))
        {
            int sortOrder = 1;
            for (ConversationGroupAbstract groupAbstract : request.getConversationGroupAbstracts())
            {
                if (groupAbstract == null || !StringUtils.hasText(groupAbstract.getGroupId()))
                    continue;

                conversationGroupMapper.updateConversationGroupSortOrder(groupAbstract.getGroupId(), userId, sortOrder++);
            }
        }

        return getConversationGroupsOfUser();
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> deleteConversationGroup(@Valid DeleteConversationGroupRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        if (!conversationGroupMapper.existsByIdAndUser(request.getGroupId(), userId))
            return ServiceResponse.buildErrorResponse(ERROR_GROUP_NOT_FOUND, "Conversation group does not exist.");

        if (request.isDeleteConversations())
            conversationMapper.deleteConversationsByGroupId(request.getGroupId(), userId);

        conversationGroupRelationMapper.deleteConversationGroupRelationsByGroupId(request.getGroupId(), userId);
        conversationGroupMapper.deleteConversationGroup(request.getGroupId(), userId);
        return ServiceResponse.buildSuccessResponse(true);
    }

    public ServiceResponse<List<ConversationGroupAbstract>> getConversationGroupsOfUser()
    {
        long userId = UserContextService.getCurrentUserId();

        List<ConversationGroupAbstract> groups = conversationGroupMapper.listConversationGroups(userId)
            .stream()
            .map(this::toConversationGroupAbstract)
            .toList();
        return ServiceResponse.buildSuccessResponse(groups);
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> addConversationsToGroup(@Valid AddConversationToGroupRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        if (!conversationGroupMapper.existsByIdAndUser(request.getConversationGroupId(), userId))
            return ServiceResponse.buildErrorResponse(ERROR_GROUP_NOT_FOUND, "Conversation group does not exist.");

        List<String> conversationIds = BusinessIdManager.normalizeIds(request.getConversationIds());
        if (CollectionUtils.isEmpty(conversationIds) || !conversationMapper.allOwnedConversationsExist(userId, conversationIds))
            return ServiceResponse.buildErrorResponse(ERROR_INVALID_CONVERSATION, "Some conversations do not exist.");

        int sortOrder = conversationGroupRelationMapper.getMaxConversationGroupRelationSortOrder(request.getConversationGroupId()) + 1;
        conversationMapper.clearConversationPinnedByIds(userId, conversationIds);
        for (String conversationId : conversationIds)
        {
            ConversationGroupRelation relation = new ConversationGroupRelation();
            relation.setCreatorId(userId);
            relation.setModifierId(userId);
            relation.setConversationId(conversationId);
            relation.setConversationGroupId(request.getConversationGroupId());
            relation.setSortOrder(sortOrder++);
            conversationGroupRelationMapper.upsertConversationGroupRelation(relation);
        }

        return ServiceResponse.buildSuccessResponse(true);
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> removeConversationsFromGroup(@Valid RemoveConversationFromGroupRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        if (!conversationGroupMapper.existsByIdAndUser(request.getConversationGroupId(), userId))
            return ServiceResponse.buildErrorResponse(ERROR_GROUP_NOT_FOUND, "Conversation group does not exist.");

        List<String> conversationIds = BusinessIdManager.normalizeIds(request.getConversationIds());
        if (CollectionUtils.isEmpty(conversationIds))
            return ServiceResponse.buildSuccessResponse(true);

        conversationGroupRelationMapper.deleteConversationGroupRelations(request.getConversationGroupId(), userId, conversationIds);
        return ServiceResponse.buildSuccessResponse(true);
    }

    private ConversationGroupAbstract toConversationGroupAbstract(ConversationGroup group)
    {
        ConversationGroupAbstract groupAbstract = new ConversationGroupAbstract();
        BeanUtils.copyProperties(group, groupAbstract);
        return groupAbstract;
    }
}
