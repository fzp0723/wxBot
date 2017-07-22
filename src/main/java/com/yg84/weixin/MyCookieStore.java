package com.yg84.weixin;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by fangzhipeng on 2017/7/16.
 */
public class MyCookieStore implements CookieStore {

    private Map<URI, List<HttpCookie>> cookieMap = new HashMap<>();

    @Override
    public void add(URI uri, HttpCookie cookie) {
        if (!cookieMap.containsKey(uri)) {
            cookieMap.put(uri, new ArrayList<>());
        }
        cookieMap.get(uri).add(cookie);
    }

    @Override
    public List<HttpCookie> get(URI uri) {
        List<HttpCookie> list = new ArrayList<>();
        cookieMap.values().forEach(list::addAll);
        return list;
    }

    @Override
    public List<HttpCookie> getCookies() {
        List<HttpCookie> ret = new ArrayList<>();
        for (List<HttpCookie> value : cookieMap.values()) {
            ret.addAll(value);
        }
        return ret;
    }

    @Override
    public List<URI> getURIs() {
        List<URI> ret = new ArrayList<>();
        for (URI uri : cookieMap.keySet()) {
            ret.add(uri);
        }
        return ret;
    }

    @Override
    public boolean remove(URI uri, HttpCookie cookie) {
        cookieMap.get(uri).remove(cookie);
        return true;
    }

    @Override
    public boolean removeAll() {
        cookieMap.clear();
        return true;
    }
}
