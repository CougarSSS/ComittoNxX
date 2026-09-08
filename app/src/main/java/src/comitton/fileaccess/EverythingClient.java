package src.comitton.fileaccess;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import src.comitton.common.DEF;
import src.comitton.common.Logcat;
import src.comitton.fileview.data.FileData;

public class EverythingClient {
    private static final String TAG = "EverythingClient";

    public static class Response {
        // 未使用フィールドだが、size/date_modifiedと同じ理由でString型で安全に受ける
        @SerializedName("total_results")
        public String totalResults;
        public List<Result> results;
    }

    public static class Result {
        public String type;
        public String name;
        public String path;
        // Everything HTTP Serverはフォルダ等で size/date_modified を空文字列で返すことがあり、
        // long型で直接受けるとGsonがNumberFormatExceptionを投げるためStringで受けて安全にパースする
        public String size;
        @SerializedName("date_modified")
        public String dateModified;
    }

    private static long parseLongSafe(String s) {
        if (s == null || s.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(s);
        }
        catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static void search(final Context context, final String query, final Handler handler, final SharedPreferences sharedPreferences) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int logLevel = Logcat.LOG_LEVEL_WARN;
                String host = sharedPreferences.getString(DEF.KEY_EVERYTHING_HOST, "");
                String port = sharedPreferences.getString(DEF.KEY_EVERYTHING_PORT, "8080");
                String user = sharedPreferences.getString(DEF.KEY_EVERYTHING_USER, "");
                String pass = sharedPreferences.getString(DEF.KEY_EVERYTHING_PASS, "");
                // EverythingX(Linux)はUnix形式のパス(スラッシュ区切り)をそのまま返す前提
                String replaceFrom = sharedPreferences.getString(DEF.KEY_EVERYTHING_REPLACE_FROM, "");
                String replaceTo = sharedPreferences.getString(DEF.KEY_EVERYTHING_REPLACE_TO, "");

                if (host.isEmpty()) {
                    sendError(handler, "Everything host is not configured.");
                    return;
                }

                try {
                    String encodedQuery = URLEncoder.encode(query, "UTF-8");
                    // path_column/size_column/date_modified_columnを指定しないと、
                    // Everything HTTP Serverはそれぞれの値をレスポンスに含めない(pathがnullになる)
                    String urlStr = "http://" + host + ":" + port + "/?search=" + encodedQuery
                            + "&json=1&path_column=1&size_column=1&date_modified_column=1";
                    Logcat.d(logLevel, "Everything API URL: " + urlStr);

                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(10000);

                    if (!user.isEmpty() && !pass.isEmpty()) {
                        String auth = user + ":" + pass;
                        String basicAuth = "Basic " + android.util.Base64.encodeToString(auth.getBytes(), android.util.Base64.NO_WRAP);
                        conn.setRequestProperty("Authorization", basicAuth);
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        Gson gson = new Gson();
                        Response apiResponse = gson.fromJson(response.toString(), Response.class);

                        ArrayList<FileData> fileList = new ArrayList<>();
                        if (apiResponse == null || apiResponse.results == null) {
                            // 0件ヒット等でresultsが無い場合
                            Message emptyMsg = handler.obtainMessage(DEF.HMSG_EVERYTHING_RESULT, fileList);
                            handler.sendMessage(emptyMsg);
                            return;
                        }
                        for (Result result : apiResponse.results) {
                            String resultPath = result.path != null ? result.path : "";
                            String resultName = result.name != null ? result.name : "";
                            if (resultName.isEmpty()) {
                                // 名前が取れない結果は不正なのでスキップ
                                continue;
                            }
                            boolean isDir = "folder".equals(result.type);
                            // EverythingXはUnix形式のパス(スラッシュ区切り)を返す前提で結合する
                            String fullPath;
                            if (resultPath.isEmpty()) {
                                fullPath = resultName;
                            } else {
                                fullPath = resultPath.endsWith("/") ? resultPath + resultName : resultPath + "/" + resultName;
                            }
                            String smbPath = fullPath;

                            // パス変換(Unixのパスは大文字小文字を区別するため、前方一致判定も大文字小文字を区別する)
                            if (!replaceFrom.isEmpty() && fullPath.startsWith(replaceFrom)) {
                                smbPath = replaceTo + fullPath.substring(replaceFrom.length());
                            }
                            if (isDir && !smbPath.endsWith("/")) {
                                smbPath += "/";
                            }

                            // SMBパスの形式調整 (smb://host/share/...)
                            if (!smbPath.startsWith("smb://")) {
                                if (smbPath.startsWith("//")) {
                                    smbPath = "smb:" + smbPath;
                                } else if (smbPath.startsWith("/")) {
                                    smbPath = "smb:/" + smbPath;
                                } else {
                                    smbPath = "smb://" + smbPath;
                                }
                            }

                            // pathにはURIを入れる（ComittoNxXのSMBアクセスはURIベース）
                            long dateModified = parseLongSafe(result.dateModified);
                            long size = parseLongSafe(result.size);
                            FileData fd = new FileData(context, resultName, smbPath, "", dateModified, size, isDir, null, "");
                            fileList.add(fd);
                        }

                        Message msg = handler.obtainMessage(DEF.HMSG_EVERYTHING_RESULT, fileList);
                        handler.sendMessage(msg);
                    } else {
                        sendError(handler, "HTTP Error: " + responseCode);
                    }
                } catch (Exception e) {
                    Logcat.e(logLevel, "Everything search error", e);
                    sendError(handler, "Error: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void sendError(Handler handler, String error) {
        Message msg = handler.obtainMessage(DEF.HMSG_TOAST, error);
        handler.sendMessage(msg);
    }
}
