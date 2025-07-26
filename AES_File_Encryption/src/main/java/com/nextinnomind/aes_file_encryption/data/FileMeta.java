package com.nextinnomind.aes_file_encryption.data;


public  class FileMeta {
    public String filename;
    public String encryptedAesKey;
    public String encryptedIv;
    public String keysIv;
    public String sha256;
    public long size;
    public String salt;

    public FileMeta(String filename, String encryptedAesKey, String encryptedIv, String keysIv,
                    String sha256, long size, String salt) {
        this.filename = filename;
        this.encryptedAesKey = encryptedAesKey;
        this.encryptedIv = encryptedIv;
        this.keysIv = keysIv;
        this.sha256 = sha256;
        this.size = size;
        this.salt = salt;
    }
}