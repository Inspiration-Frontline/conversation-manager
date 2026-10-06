# Conversation Manager SQL Order

Forward migrations live in `SQLs/Forward/` and must run in the order below. Rollback scripts live in
`SQLs/Rollback/`; they are paired recovery operations and are not part of a forward replay. No SQL
file sits directly in `SQLs/`.

## Forward Order

1. `Forward/20260520_InitializeTables.sql`
2. `Forward/20260718_AddConversationFiles.sql`
3. `Forward/20260719_ConversationSharing.sql`
4. `Forward/20260719_ForkHistory.sql`
5. `Forward/20260721_ConversationGroups.sql`
6. `Forward/20260723_ConversationRoundReferences.sql`
7. `Forward/20260725_NumericConversationGroupIds.sql`
8. `Forward/20260801_ConversationRoundTraceId.sql`
9. `Forward/20260806_DropConversationRoundTraceIdIndex.sql`
10. `Forward/20260815_ExpandToolExecutionStatus.sql`
11. `Forward/20260815_StreamableHttpMcpDispatch.sql`
12. `Forward/20260816_AddProgressAuditColumns.sql`
13. `Forward/20260817_DefaultRoundMutationModificationTime.sql`
14. `Forward/20260825_ConsolidateRoundTurnExecutionExpand.sql`
15. `Forward/20260825_ConsolidateRoundTurnExecutionContract.sql`
16. `Forward/20260830_FileResourceVariants.sql`
17. `Forward/20260831_RestoreForkedRoundFiles.sql`
18. `Forward/20260831_UseOneBasedRoundFileOrder.sql`
19. `Forward/20260907_GeneratedFiles.sql`
20. `Forward/20260908_FileResourceOrigin.sql`
21. `Forward/20260912_TaskAgentExecutionIdempotency.sql`
22. `Forward/20260913_RestoreForkedGeneratedFiles.sql`

The two `20260825` files are a committed legacy naming exception: the Expand migration must run
before Contract even though their action names sort in the opposite order. Future same-day
dependencies must use sortable sequence numbers in their filenames.

## Rollback Pairs

Every forward migration has a matching file in `SQLs/Rollback/` with the same date and change name,
for example `Forward/20260907_GeneratedFiles.sql` pairs with
`Rollback/20260907_RollbackGeneratedFiles.sql`. Rollback content is unchanged; the two Phase 15
increment rollbacks gained the `Rollback` prefix so every file in the directory uses one scheme.

Versioned migrations contain durable schema/function changes and the deterministic transformations
required by those changes. Environment-specific, one-time data repairs do not belong in these
directories.
