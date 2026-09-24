package src.comitton.fileaccess;

/**
 * 書庫管理タブが対象とするフォルダのパス定義。
 * 実際のデータ取得はLibrarySyncClient(bookmark-sync-server /library)経由に統一したため、
 * ここにはLibraryCache.resolveTitle()のパス判定で使う定数のみ残している。
 */
public class EverythingLibraryClient {
    public static final String LIBRARY_PATH_MANGA = "/srv/samba/漫画";
    public static final String LIBRARY_PATH_ARIA2C = "/srv/samba/Aria2c_DL";
}
