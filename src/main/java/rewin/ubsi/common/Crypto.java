package rewin.ubsi.common;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * 利用org.bouncycastle实现国密SM2/SM3/SM4算法的工具
 */
public class Crypto {

    /** SM3算法的数据长度 */
    public final static int DIGEST_SIZE = 32;

    /** SM3数据散列
     *  @param data 源数据
     *  @return 散列值，长度32字节
     */
    public static byte[] sm3Digest(byte[] data) {
        SM3Digest sm3 = new SM3Digest();
        byte[] md = new byte[DIGEST_SIZE];
        sm3.update(data, 0, data.length);
        sm3.doFinal(md, 0);
        return md;
    }

    /** HMAC数据签名
     *  @param data 源数据
     *  @param key 密钥，长度不限
     *  @return 签名，长度32字节
     */
    public static byte[] sm3HMAC(byte[] data, byte[] key) {
        if ( key.length < DIGEST_SIZE ) {
            byte[] newkey = new byte[DIGEST_SIZE];
            System.arraycopy(key, 0, newkey, 0, key.length);
            key = newkey;
        }
        byte[] ipad = new byte[DIGEST_SIZE];
        byte[] opad = new byte[DIGEST_SIZE];
        for ( int i = 0; i < DIGEST_SIZE; i ++ ) {
            ipad[i] = (byte)(key[i] ^ 0x36);
            opad[i] = (byte)(key[i] ^ 0x5c);
        }
        byte[] ihash = sm3Digest(Util.mergeBytes(ipad, data));
        return sm3Digest(Util.mergeBytes(opad, ihash));
    }

    /** SM4算法的数据分组长度 */
    public final static int BLOCK_SIZE = 16;

    /** SM4加密数据的长度
     *  @param size 源数据的长度
     *  @return 加密数据的长度
     */
    public static int sm4EncryptSize(int size) {
        return size + BLOCK_SIZE - ( size % BLOCK_SIZE );
    }

    /** SM4数据加密
     *  @param data 源数据，以128位(16字节)为一组，会自动补位
     *  @param key 加密密钥(长度必须16字节)
     *  @return 加密数据
     */
    public static byte[] sm4EncryptEcb(byte[] data, byte[] key) {
        int size = data.length;
        int rsize = sm4EncryptSize(size);
        int delta = rsize - size;
        byte[] temp = new byte[BLOCK_SIZE];
        System.arraycopy(data, rsize - BLOCK_SIZE, temp, 0, BLOCK_SIZE - delta);
        for ( int i = 0; i < delta; i ++ )
            temp[BLOCK_SIZE - delta + i] = (byte)delta;

        byte[] res = new byte[rsize];
        SM4Engine sm4 = new SM4Engine();
        sm4.init(true, new KeyParameter(key));
        for ( int i = 0; i < size / BLOCK_SIZE; i ++ )
            sm4.processBlock(data, i * BLOCK_SIZE, res, i * BLOCK_SIZE);
        sm4.processBlock(temp, 0, res, rsize - BLOCK_SIZE);
        return res;
    }

    /** SM4数据解密
     *  @param data 加密数据(长度必须为16字节的整倍数)
     *  @param key 加密密钥(长度必须16字节)
     *  @return 解密数据
     */
    public static byte[] sm4DecryptEcb(byte[] data, byte[] key) {
        byte[] temp = new byte[BLOCK_SIZE];
        SM4Engine sm4 = new SM4Engine();
        sm4.init(false, new KeyParameter(key));
        sm4.processBlock(data, data.length - BLOCK_SIZE, temp, 0);

        int delta = temp[BLOCK_SIZE - 1];
        byte[] res = new byte[data.length - delta];
        for ( int i = 0; i < data.length / BLOCK_SIZE - 1; i ++ )
            sm4.processBlock(data, i * BLOCK_SIZE, res, i * BLOCK_SIZE);

        System.arraycopy(temp, 0, res, data.length - BLOCK_SIZE, BLOCK_SIZE - delta);
        return res;
    }

}
