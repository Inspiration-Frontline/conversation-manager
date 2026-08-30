package ifl.agentbreaker.conversationmanager.domain.constants;

/** User-facing categories that determine file parsing and model-input behavior. */
public enum ConversationFileKind
{
    /** Paginated or word-processing document. */
    DOCUMENT,
    /** Raster image supplied to a multimodal model. */
    IMAGE,
    /** Plain or structured text file. */
    TEXT,
    /** Workbook whose cells are extracted as text. */
    SPREADSHEET,
    /** Slide deck whose text shapes are extracted. */
    PRESENTATION,
}
