package com.github.k7t3.tcv.app.secure;

/** OSで保護するランダムな秘密を、公開用の識別子で読み書きする境界。 */
interface OsSecretStore {
    /** 呼出元が消去する独立した配列を返す。未登録の場合はnull、利用拒否・読取障害は例外で通知する。 */
    byte[] read(String id);

    /** 保存の完了後に戻る。借用した配列を保持せず、秘密をログやプロセス引数へ公開しない。 */
    void save(String id, byte[] secret);
}
