package ifl.agentbreaker.conversationmanager.services.files;

/**
 * Minimal projection of one file resource resolved inside an authorized shared snapshot.
 *
 * @param fileResourceId internal resource identity used to load the ready preview variant
 * @param kind durable file kind checked against the image preview contract
 * @param status durable file status checked against the ready preview contract
 */
record SharedPreviewTarget(long fileResourceId, String kind, String status)
{
}
