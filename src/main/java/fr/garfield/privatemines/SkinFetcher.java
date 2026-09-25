package fr.garfield.privatemines;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Récupère le skin d'un compte Minecraft existant via l'API publique de Mojang.
 *
 * <p>Deux appels HTTP :
 * <ol>
 *   <li>pseudo → UUID (api.mojang.com)</li>
 *   <li>UUID → propriété "textures" (value base64 + signature) (sessionserver.mojang.com)</li>
 * </ol>
 *
 * <p><b>À appeler en ASYNC</b> (jamais sur le thread principal : ce sont des requêtes réseau).
 */
public final class SkinFetcher {

    private SkinFetcher() {}

    /**
     * @return un tableau {value, signature} (les 2 propriétés du skin), ou null en cas d'échec.
     */
    public static String[] fetch(String username) {
        try {
            // 1) pseudo -> UUID
            String uuidJson = get("https://api.mojang.com/users/profiles/minecraft/" + username);
            if (uuidJson == null) return null;
            String uuid = extract(uuidJson, "id");
            if (uuid == null) return null;

            // 2) UUID -> textures (avec ?unsigned=false pour obtenir la signature)
            String profileJson = get(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            if (profileJson == null) return null;

            String value = extract(profileJson, "value");
            String signature = extract(profileJson, "signature");
            if (value == null) return null;
            return new String[]{ value, signature == null ? "" : signature };
        } catch (Exception e) {
            return null;
        }
    }

    // GET simple ; renvoie le corps texte, ou null si code != 200.
    private static String get(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream();
                 Scanner sc = new Scanner(in, "UTF-8")) {
                sc.useDelimiter("\\A");
                return sc.hasNext() ? sc.next() : null;
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // Extraction ULTRA simple d'une valeur "key":"..." dans un JSON plat (évite une lib JSON).
    private static String extract(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) return null;
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) return null;
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;
        return json.substring(firstQuote + 1, secondQuote);
    }
}
