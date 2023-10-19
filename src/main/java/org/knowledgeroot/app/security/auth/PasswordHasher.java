package org.knowledgeroot.app.security.auth;

import jakarta.validation.constraints.NotNull;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/*
 * This class implements the password hashing
 *
 * A hash is a $-separated, human readable string of tokens, formatted
 * like
 * $<methodIndex>$<repetition>$<salt>$<cryptohash>
 *
 * The method index is an integer standing for
 *  1 = SHA-1
 *  2 = SHA-256
 *  3 = SHA-384 (not guaranteed to exist on every Java implementation)
 *  4 = SHA-512 (not guaranteed to exist on every Java implementation)
 *  5 = <unused, was once MD2>
 *  6 = MD5
 *
 * Repetition is an integer > 0 indicating, how many times
 * the hashing method is repeatedly applied, taking as input the
 * result of the previous round, and starting with the salted password.
 * The count should be 1. If you think you really need double, triple hashing,
 * then the chosen method is likely cryptographically insecure, and should
 * be replaced anyway.
 *
 * The salt is an affix to the password. It is usually randomly generated,
 * and it prevents dictionary attacks, since common passwords like 'admin'
 * cannot be guessed from their hash value any more.
 *
 * The cryptohash is the result of the hashing process. Its binary
 * value is encoded in hex. Its length is the same for any
 * fixed method, but varies, if the method changes.
 */
public class PasswordHasher {

	/**
	 * installed hash algorithms
	 * The order of elements equals that given in CryptoString.cpp,
	 * but the ordinal value is one less, as enumerations have
	 * zero-based ordinals
	 *
	 * @author fhabermann
	 */
	public static enum HASH_METHOD {
		SHA1("SHA-1"),
		SHA256("SHA-256"),
		SHA384("SHA-384"),
		SHA512("SHA-512"),
		MD2_UNAVAILABLE(""),
		MD5("MD5");

		private String digestMethodName;

		HASH_METHOD(String digestMethodName)
		{
			this.digestMethodName = digestMethodName;
		}

		/**
		 *
		 * @return whether the HASH_METHOD is supported
		 */
		public boolean isValid()
		{
			return !digestMethodName.isEmpty();
		}

		private void checkValid()
		{
			if (!isValid())
				throw new IllegalArgumentException("unsupported hash method " + name());
		}

		/**
		 *
		 * @return method name suitable for class MessageDigest
		 */
		public final String digestMethodName()
		{
			checkValid();
			return digestMethodName;
		}

		/**
		 * encoding used in a database hash
		 *
		 * @return
		 */
		private final int indexOf() {
			return ordinal() + 1;
		}

		/**
		 * inverse of indexOf
		 *
		 * @param i
		 * @return
		 */
		private static HASH_METHOD fromIndex(int i) {
			HASH_METHOD result = HASH_METHOD.values()[i - 1];
			result.checkValid();
			return result;
		}

	}

	/**
	 * verify if given plain text password is the same password with the given
	 * hash
	 *
	 * @param password
	 *            plain text password
	 * @param passwordHash
	 * @return
	 */
	public static final boolean verify(@NotNull String password, @NotNull String passwordHash) {
		String[] parts = passwordHash.split("\\$");
		return parts.length == 5
				&& new PasswordHasher(password, HASH_METHOD.fromIndex(Integer.parseUnsignedInt(parts[1])),
						Integer.parseUnsignedInt(parts[2]), parts[3]).toString().equals(passwordHash);
	}

	/**
	 * create hash value from password
	 *
	 * @param password
	 * @param hashMethod
	 * @param repetition
	 * @return
	 */
	public static final String hash(String password, HASH_METHOD hashMethod, int repetition) {
		return new PasswordHasher(password, hashMethod, repetition,
					DatatypeConverter
						.printHexBinary(
								createRandomString(SALT_LENGTH)))
				.toString();
	}

	// ======================= private stuff ================================

	private static final int SALT_LENGTH = 8;

	/**
	 * The constructor is deliberately private to avoid keeping password related
	 * data in memory.
	 *
	 * repetition > 1 entails an iterated hashing scheme hasMethod is one string
	 * from HASH_METHOD, case does not matter the password is salted with salt
	 *
	 * @param password
	 * @param hashMethod
	 * @param repetition
	 * @param salt
	 */
	private PasswordHasher(String password, HASH_METHOD hashMethod,
                           int repetition, String salt) {
		method = hashMethod;
		this.repetition = repetition;
		this.salt = salt;

		// the password is never stored and immediately hashed for
		// security reasons

		// remark: hashing the salt is a costly nonsense. It does not
		// add to the cryptographic value
		String hashedSalt = DatatypeConverter.printHexBinary(createHash(salt, HASH_METHOD.MD5));
		hash = password;

		// remark: double, triple hashing: consider using a repetition count of
		// 1. If you need more, the chosen hashing method is likely
		// cryptographically insecure and should be replaced anyway.
		for (int i = -1; ++i < repetition;)
			hash = DatatypeConverter.printHexBinary(createHash(hash + hashedSalt + i, method));
	}

	/**
	 * hash formatted for database
	 */
	@Override
	public String toString() {
		return String.join("$",
				"",
				String.valueOf(method.indexOf()),
				String.valueOf(repetition),
				salt,
				hash);
	}

	/*
	 * various conversions of HASH_METHOD
	 */

	/**
	 * The JAVA documentation says, getInstanceStrong will always succeed.
	 * The catch branch is dead code, and we do not propagate the exception
	 *
	 * @return
	 */
	private static SecureRandom getRNG() {
		try {
			return SecureRandom.getInstanceStrong();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("unexpected failure of SecureRandom.getInstanceStrong", e);
		}
	}

	/*
	 * data used in hashing
	 */
	private HASH_METHOD method;
	private int repetition;
	private String salt;

	/*
	 * the computed hash
	 */
	private String hash;

	/*
	 * cryptographic helpers
	 *
	 * Hint: The return type of cryptographic functions in this class comply to
	 * the following rule: byte[] always contains binary data String contains a
	 * hex encoded string (usually of some binary data), or some human readable
	 * user input
	 */

	/**
	 * random bytes
	 *
	 * @param length
	 * @return
	 */
	private static byte[] createRandomString(int length) {
		byte[] bytes = new byte[length];
		getRNG().nextBytes(bytes);
		return bytes;
	}

	/**
	 * creates a binary hash from input
	 *
	 * @param toBeHashed
	 * @param method
	 * @return
	 */
	private byte[] createHash(String toBeHashed, HASH_METHOD method) {
		try {
			MessageDigest hasher = MessageDigest.getInstance(method.digestMethodName());
			hasher.update(toBeHashed.getBytes());
			return hasher.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("unsupported hash algorithm " + method.digestMethodName(), e);
		}
	}
}