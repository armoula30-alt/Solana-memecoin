
@Entity(tableName = "paper_watchlist")
data class PaperWatchlistEntity(
    @PrimaryKey val mint: String,
    val addedAt: Long = System.currentTimeMillis(),
    val note: String? = null
)
