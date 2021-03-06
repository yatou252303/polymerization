package com.polymerization.core.okhttp.Cookie;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.safframework.log.L;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import com.polymerization.core.utils.sptool.SpManager;
import com.polymerization.core.utils.sptool.SpTool;

/**
 * Author: yangweichao
 * Date:   2018/11/14 2:32 PM
 * Description:
 */


public final class PersistentCookieStore {
    private final SpTool cookieSp;
    private final String cookieSpName = "spCookie";
    private final Map<String, ConcurrentHashMap<String, Cookie>> cookies;

    public PersistentCookieStore() {
        cookieSp = SpManager.getSp(cookieSpName);
        cookies = new HashMap<>();

        // 将持久化的cookies缓存到内存中 即map cookies
        Map<String, ?> prefsMap = cookieSp.getAll();
        for (Map.Entry<String, ?> entry : prefsMap.entrySet()) {
            String[] cookieNames = TextUtils.split((String) entry.getValue(), ",");
            for (String name : cookieNames) {
                String encodedCookie = cookieSp.getString(name, null);
                if (encodedCookie != null) {
                    Cookie decodedCookie = decodeCookie(encodedCookie);
                    if (decodedCookie != null) {
                        if (!cookies.containsKey(entry.getKey())) {
                            cookies.put(entry.getKey(), new ConcurrentHashMap<String, Cookie>());
                        }
                        cookies.get(entry.getKey()).put(name, decodedCookie);
                    }
                }
            }
        }
    }

    /**
     * 另多域名共用Cookie时配置
     */
    public String compatibilityMultitypeHost(String urlHost) {
//        if (urlHost.equals("m.nuojinsuo.cn")
//                || urlHost.equals("ups.nuojinsuo.cn")
//                || urlHost.equals("pay.nuojinsuo.cn")
//                || urlHost.equals("static.nuojinsuo.cn")) {
//            return "nuojinsuo.cn";
//        }
        return urlHost;
    }

    /* 此种方式会导致程序崩溃
    protected String getCookieToken(@NonNull Cookie cookie) {
        return cookie.name() + "@" + cookie.domain();
    }

    public void add(@NonNull HttpUrl url, @NonNull Cookie cookie) {
        String host = compatibilityMultitypeHost(url.host());
        String name = getCookieToken(cookie);

        //将cookies缓存到内存中 如果缓存过期 就重置此cookie
        if (!cookie.persistent()) {
            if (!cookies.containsKey(host)) {
                cookies.put(host, new ConcurrentHashMap<String, Cookie>());
            }
            cookies.get(host).put(name, cookie);
        } else {
            if (cookies.containsKey(host)) {
                cookies.get(host).remove(name);
            }
        }

        //将cookies持久化到本地
        cookieSp.putString(host, TextUtils.join(",", cookies.get(host).keySet()));
        cookieSp.putString(name, encodeCookie(new NetCookies(cookie)));
    }*/

    protected String getCookieToken(@NonNull Cookie cookie) {
        return cookie.name();
    }

    public void add(@NonNull HttpUrl url, @NonNull Cookie cookie) {
        String host = compatibilityMultitypeHost(url.host());
        String name = getCookieToken(cookie);
        if (!cookies.containsKey(host)) {
            cookies.put(host, new ConcurrentHashMap<String, Cookie>());
        }
        cookies.get(host).put(name, cookie);
        //将cookies持久化到本地
        cookieSp.putString(host, TextUtils.join(",", cookies.get(host).keySet()));
        cookieSp.putString(name, encodeCookie(new HttpCookies(cookie)));
    }

    public List<Cookie> getCookies(@NonNull HttpUrl url) {
        String host = compatibilityMultitypeHost(url.host());
        ArrayList<Cookie> ret = new ArrayList<>();
        long time = System.currentTimeMillis();
        if (cookies.containsKey(host)) {
            ConcurrentHashMap<String, Cookie> maps = cookies.get(host);
            for (Map.Entry<String, Cookie> entry : maps.entrySet()) {
                if (entry.getValue().expiresAt() > time) {
                    ret.add(entry.getValue());
                }
            }
        }
        return ret;
    }

    public List<Cookie> getCookies() {
        ArrayList<Cookie> ret = new ArrayList<>();
        long time = System.currentTimeMillis();
        for (Map.Entry<String, ConcurrentHashMap<String, Cookie>> entry : cookies.entrySet()) {
            ConcurrentHashMap<String, Cookie> maps = entry.getValue();
            for (Map.Entry<String, Cookie> cookieEntry : maps.entrySet()) {
                if (cookieEntry.getValue().expiresAt() > time) {
                    ret.add(cookieEntry.getValue());
                }
            }
        }
        return ret;
    }

    public void removeAll() {
        cookies.clear();
        cookieSp.clear();
    }

    public boolean remove(@NonNull HttpUrl url, @NonNull Cookie cookie) {
        String host = compatibilityMultitypeHost(url.host());
        String name = getCookieToken(cookie);
        if (cookies.containsKey(host) && cookies.get(host).containsKey(name)) {
            cookies.get(host).remove(name);
            if (cookieSp.contains(name)) {
                cookieSp.remove(name);
            }
            cookieSp.putString(host, TextUtils.join(",", cookies.get(host).keySet()));
            return true;
        } else {
            return false;
        }
    }

    /**
     * cookies 序列化成 string
     *
     * @param cookie 要序列化的cookie
     * @return 序列化之后的string
     */
    protected String encodeCookie(@NonNull HttpCookies cookie) {
        if (cookie == null)
            return null;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(os);
            outputStream.writeObject(cookie);
        } catch (IOException pE) {
            L.e(pE.getMessage());
            return null;
        }
        return byteArrayToHexString(os.toByteArray());
    }

    /**
     * 将字符串反序列化成cookies
     *
     * @param cookieString cookies string
     * @return cookie object
     */
    protected Cookie decodeCookie(@NonNull String cookieString) {
        byte[] bytes = hexStringToByteArray(cookieString);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        Cookie cookie = null;
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            cookie = ((HttpCookies) objectInputStream.readObject()).getCookies();
        } catch (IOException pE) {
            L.e(pE.getMessage());
        } catch (ClassNotFoundException pE) {
            L.e(pE.getMessage());
        }
        return cookie;
    }

    /**
     * 二进制数组转十六进制字符串
     *
     * @param bytes byte array to be converted
     * @return string containing hex values
     */
    protected String byteArrayToHexString(@NonNull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte element : bytes) {
            int v = element & 0xff;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    /**
     * 十六进制字符串转二进制数组
     *
     * @param hexString string of hex-encoded values
     * @return decoded byte array
     */
    protected byte[] hexStringToByteArray(@NonNull String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }
}
