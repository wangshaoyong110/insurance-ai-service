package com.insure.ai.common.crypto;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.crypto.signers.StandardDSAEncoding;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * SM2 签名工具(国密椭圆曲线公钥密码算法,GB/T 32918)
 * <p>
 * 用于替换原有 HMAC-SHA256 的 JWT 签名方案:私钥签名、公钥验签,
 * 服务端只需持有私钥,校验方(网关、其他服务)持有公钥即可验签,
 * 不必共享同一份对称密钥。
 * </p>
 * <p>
 * 密钥对由主密钥 {@code insure.jwt.secret} 通过 SM3 派生,保证进程重启后
 * 旧 token 仍然有效;更换主密钥等同于吊销全部历史 token。
 * </p>
 *
 * @author shaoyong.wang
 */
public final class Sm2Utils {

    /** 签名算法标识,写入 JWT Header 的 alg 字段 */
    public static final String ALGORITHM = "SM2-SM3";

    /** SM2 推荐曲线 sm2p256v1 */
    private static final X9ECParameters X9 = GMNamedCurves.getByName("sm2p256v1");

    /** 曲线域参数 */
    private static final ECDomainParameters DOMAIN =
            new ECDomainParameters(X9.getCurve(), X9.getG(), X9.getN(), X9.getH());

    /** 默认用户标识(GB/T 32918 推荐值),参与签名摘要计算,双方必须一致 */
    private static final byte[] DEFAULT_USER_ID =
            "1234567812345678".getBytes(StandardCharsets.UTF_8);

    private Sm2Utils() {
    }

    /**
     * SM2 密钥对
     *
     * @param privateKey   私钥标量 d
     * @param publicKey    公钥点
     * @param publicKeyHex 公钥十六进制串(04 开头未压缩格式),用于对外公布
     */
    public record KeyPair(BigInteger privateKey, ECPublicKeyParameters publicKey, String publicKeyHex) {
    }

    /**
     * 由主密钥口令确定性地派生 SM2 密钥对
     * <p>d 取 SM3(secret) 的结果并对曲线阶取模,保证落在合法区间 [1, n-2]</p>
     *
     * @param secret 主密钥口令
     * @return 密钥对
     */
    public static KeyPair deriveKeyPair(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("签名主密钥不能为空");
        }
        byte[] seed = Sm3Utils.hash(secret + "|SM2-SIGN");
        BigInteger n = DOMAIN.getN();
        BigInteger d = new BigInteger(1, seed).mod(n.subtract(BigInteger.TWO)).add(BigInteger.ONE);
        ECPoint point = DOMAIN.getG().multiply(d).normalize();
        ECPublicKeyParameters publicKey = new ECPublicKeyParameters(point, DOMAIN);
        return new KeyPair(d, publicKey, Sm3Utils.toHex(point.getEncoded(false)));
    }

    /**
     * 签名
     *
     * @param data 待签名数据
     * @param pair 密钥对(使用私钥)
     * @return DER 编码的签名值
     */
    public static byte[] sign(byte[] data, KeyPair pair) {
        SM2Signer signer = new SM2Signer(StandardDSAEncoding.INSTANCE, new SM3Digest());
        signer.init(true, new ParametersWithID(new ECPrivateKeyParameters(pair.privateKey(), DOMAIN), DEFAULT_USER_ID));
        signer.update(data, 0, data.length);
        try {
            return signer.generateSignature();
        } catch (Exception e) {
            throw new IllegalStateException("SM2 签名失败", e);
        }
    }

    /**
     * 验签
     *
     * @param data      原始数据
     * @param signature DER 编码的签名值
     * @param pair      密钥对(使用公钥)
     * @return 验签是否通过
     */
    public static boolean verify(byte[] data, byte[] signature, KeyPair pair) {
        if (signature == null || signature.length == 0) {
            return false;
        }
        try {
            SM2Signer signer = new SM2Signer(StandardDSAEncoding.INSTANCE, new SM3Digest());
            signer.init(false, new ParametersWithID(pair.publicKey(), DEFAULT_USER_ID));
            signer.update(data, 0, data.length);
            return signer.verifySignature(signature);
        } catch (Exception e) {
            // 签名格式非法时按验签失败处理,不向上抛异常,避免被构造异常流量放大
            return false;
        }
    }

    /**
     * Base64URL 编码(无填充),用于 JWT 各段的编码
     *
     * @param data 字节数组
     * @return 编码结果
     */
    public static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Base64URL 解码(容忍缺失填充)
     *
     * @param text 编码文本
     * @return 字节数组
     */
    public static byte[] base64UrlDecode(String text) {
        return Base64.getUrlDecoder().decode(text);
    }

    /**
     * 公钥指纹(便于在配置中心核对密钥一致性)
     *
     * @param pair 密钥对
     * @return SM3 摘要十六进制前 16 位
     */
    public static String fingerprint(KeyPair pair) {
        byte[] hash = Sm3Utils.hash(Arrays.copyOf(pair.publicKeyHex().getBytes(StandardCharsets.UTF_8), 32));
        return Sm3Utils.toHex(hash).substring(0, 16);
    }
}
