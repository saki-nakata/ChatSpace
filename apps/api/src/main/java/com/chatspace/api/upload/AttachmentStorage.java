package com.chatspace.api.upload;

import org.springframework.core.io.Resource;

/**
 * 添付ファイルの実体I/Oを抽象化する(ローカルディスク/オブジェクトストレージ)。{@link #exists(String)}は本文を転送しない軽量な 存在確認のみを行い、{@link
 * #load(String)}(本文取得)は{@link #exists(String)}が{@code true}を返し、かつライブ権限再チェックに
 * 成功した後にのみ呼び出すこと(認可拒否されるリクエストのたびにオブジェクトストレージへ本文転送させないため、インフラ構成書§7.1)。
 */
public interface AttachmentStorage {

  void put(String storageKey, byte[] content);

  boolean exists(String storageKey);

  Resource load(String storageKey);

  void delete(String storageKey);
}
