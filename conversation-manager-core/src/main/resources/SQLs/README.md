# Conversation Manager SQL Order

Run forward migrations in the following order. Rollback scripts are paired recovery operations and
are not part of a forward replay.

1. `20260520_InitializeTables.sql`
2. `20260718_AddConversationFiles.sql`
3. `20260719_ConversationSharing.sql`
4. `20260719_ForkHistory.sql`
5. `20260721_ConversationGroups.sql`
6. `20260723_ConversationRoundReferences.sql`
7. `20260725_NumericConversationGroupIds.sql`
8. `20260801_ConversationRoundTraceId.sql`
9. `20260806_DropConversationRoundTraceIdIndex.sql`
10. `20260815_ExpandToolExecutionStatus.sql`
11. `20260815_StreamableHttpMcpDispatch.sql`
12. `20260816_AddProgressAuditColumns.sql`
13. `20260817_DefaultRoundMutationModificationTime.sql`
14. `20260825_ConsolidateRoundTurnExecutionExpand.sql`
15. `20260825_ConsolidateRoundTurnExecutionContract.sql`
16. `20260830_FileResourceVariants.sql`
17. `20260831_RestoreForkedRoundFiles.sql`
18. `20260831_UseOneBasedRoundFileOrder.sql`

The two `20260825` files are a committed legacy naming exception: the Expand migration must run
before Contract even though their action names sort in the opposite order. Future same-day
dependencies must use sortable sequence numbers in their filenames.

Versioned migrations contain durable schema/function changes and deterministic transformations
required by those changes. Environment-specific, one-time data repairs do not belong in this
directory.
