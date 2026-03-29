var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// node_modules/jose/dist/webapi/lib/buffer_utils.js
var encoder = new TextEncoder();
var decoder = new TextDecoder();
var MAX_INT32 = 2 ** 32;
function concat(...buffers) {
  const size = buffers.reduce((acc, { length }) => acc + length, 0);
  const buf = new Uint8Array(size);
  let i = 0;
  for (const buffer of buffers) {
    buf.set(buffer, i);
    i += buffer.length;
  }
  return buf;
}
__name(concat, "concat");
function encode(string) {
  const bytes = new Uint8Array(string.length);
  for (let i = 0; i < string.length; i++) {
    const code = string.charCodeAt(i);
    if (code > 127) {
      throw new TypeError("non-ASCII string encountered in encode()");
    }
    bytes[i] = code;
  }
  return bytes;
}
__name(encode, "encode");

// node_modules/jose/dist/webapi/lib/base64.js
function encodeBase64(input) {
  if (Uint8Array.prototype.toBase64) {
    return input.toBase64();
  }
  const CHUNK_SIZE = 32768;
  const arr = [];
  for (let i = 0; i < input.length; i += CHUNK_SIZE) {
    arr.push(String.fromCharCode.apply(null, input.subarray(i, i + CHUNK_SIZE)));
  }
  return btoa(arr.join(""));
}
__name(encodeBase64, "encodeBase64");
function decodeBase64(encoded) {
  if (Uint8Array.fromBase64) {
    return Uint8Array.fromBase64(encoded);
  }
  const binary = atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}
__name(decodeBase64, "decodeBase64");

// node_modules/jose/dist/webapi/util/base64url.js
function decode(input) {
  if (Uint8Array.fromBase64) {
    return Uint8Array.fromBase64(typeof input === "string" ? input : decoder.decode(input), {
      alphabet: "base64url"
    });
  }
  let encoded = input;
  if (encoded instanceof Uint8Array) {
    encoded = decoder.decode(encoded);
  }
  encoded = encoded.replace(/-/g, "+").replace(/_/g, "/");
  try {
    return decodeBase64(encoded);
  } catch {
    throw new TypeError("The input to be decoded is not correctly encoded.");
  }
}
__name(decode, "decode");
function encode2(input) {
  let unencoded = input;
  if (typeof unencoded === "string") {
    unencoded = encoder.encode(unencoded);
  }
  if (Uint8Array.prototype.toBase64) {
    return unencoded.toBase64({ alphabet: "base64url", omitPadding: true });
  }
  return encodeBase64(unencoded).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
__name(encode2, "encode");

// node_modules/jose/dist/webapi/lib/crypto_key.js
var unusable = /* @__PURE__ */ __name((name, prop = "algorithm.name") => new TypeError(`CryptoKey does not support this operation, its ${prop} must be ${name}`), "unusable");
var isAlgorithm = /* @__PURE__ */ __name((algorithm, name) => algorithm.name === name, "isAlgorithm");
function getHashLength(hash) {
  return parseInt(hash.name.slice(4), 10);
}
__name(getHashLength, "getHashLength");
function checkHashLength(algorithm, expected) {
  const actual = getHashLength(algorithm.hash);
  if (actual !== expected)
    throw unusable(`SHA-${expected}`, "algorithm.hash");
}
__name(checkHashLength, "checkHashLength");
function getNamedCurve(alg) {
  switch (alg) {
    case "ES256":
      return "P-256";
    case "ES384":
      return "P-384";
    case "ES512":
      return "P-521";
    default:
      throw new Error("unreachable");
  }
}
__name(getNamedCurve, "getNamedCurve");
function checkUsage(key, usage) {
  if (usage && !key.usages.includes(usage)) {
    throw new TypeError(`CryptoKey does not support this operation, its usages must include ${usage}.`);
  }
}
__name(checkUsage, "checkUsage");
function checkSigCryptoKey(key, alg, usage) {
  switch (alg) {
    case "HS256":
    case "HS384":
    case "HS512": {
      if (!isAlgorithm(key.algorithm, "HMAC"))
        throw unusable("HMAC");
      checkHashLength(key.algorithm, parseInt(alg.slice(2), 10));
      break;
    }
    case "RS256":
    case "RS384":
    case "RS512": {
      if (!isAlgorithm(key.algorithm, "RSASSA-PKCS1-v1_5"))
        throw unusable("RSASSA-PKCS1-v1_5");
      checkHashLength(key.algorithm, parseInt(alg.slice(2), 10));
      break;
    }
    case "PS256":
    case "PS384":
    case "PS512": {
      if (!isAlgorithm(key.algorithm, "RSA-PSS"))
        throw unusable("RSA-PSS");
      checkHashLength(key.algorithm, parseInt(alg.slice(2), 10));
      break;
    }
    case "Ed25519":
    case "EdDSA": {
      if (!isAlgorithm(key.algorithm, "Ed25519"))
        throw unusable("Ed25519");
      break;
    }
    case "ML-DSA-44":
    case "ML-DSA-65":
    case "ML-DSA-87": {
      if (!isAlgorithm(key.algorithm, alg))
        throw unusable(alg);
      break;
    }
    case "ES256":
    case "ES384":
    case "ES512": {
      if (!isAlgorithm(key.algorithm, "ECDSA"))
        throw unusable("ECDSA");
      const expected = getNamedCurve(alg);
      const actual = key.algorithm.namedCurve;
      if (actual !== expected)
        throw unusable(expected, "algorithm.namedCurve");
      break;
    }
    default:
      throw new TypeError("CryptoKey does not support this operation");
  }
  checkUsage(key, usage);
}
__name(checkSigCryptoKey, "checkSigCryptoKey");

// node_modules/jose/dist/webapi/lib/invalid_key_input.js
function message(msg, actual, ...types) {
  types = types.filter(Boolean);
  if (types.length > 2) {
    const last = types.pop();
    msg += `one of type ${types.join(", ")}, or ${last}.`;
  } else if (types.length === 2) {
    msg += `one of type ${types[0]} or ${types[1]}.`;
  } else {
    msg += `of type ${types[0]}.`;
  }
  if (actual == null) {
    msg += ` Received ${actual}`;
  } else if (typeof actual === "function" && actual.name) {
    msg += ` Received function ${actual.name}`;
  } else if (typeof actual === "object" && actual != null) {
    if (actual.constructor?.name) {
      msg += ` Received an instance of ${actual.constructor.name}`;
    }
  }
  return msg;
}
__name(message, "message");
var invalidKeyInput = /* @__PURE__ */ __name((actual, ...types) => message("Key must be ", actual, ...types), "invalidKeyInput");
var withAlg = /* @__PURE__ */ __name((alg, actual, ...types) => message(`Key for the ${alg} algorithm must be `, actual, ...types), "withAlg");

// node_modules/jose/dist/webapi/util/errors.js
var JOSEError = class extends Error {
  static {
    __name(this, "JOSEError");
  }
  static code = "ERR_JOSE_GENERIC";
  code = "ERR_JOSE_GENERIC";
  constructor(message2, options) {
    super(message2, options);
    this.name = this.constructor.name;
    Error.captureStackTrace?.(this, this.constructor);
  }
};
var JWTClaimValidationFailed = class extends JOSEError {
  static {
    __name(this, "JWTClaimValidationFailed");
  }
  static code = "ERR_JWT_CLAIM_VALIDATION_FAILED";
  code = "ERR_JWT_CLAIM_VALIDATION_FAILED";
  claim;
  reason;
  payload;
  constructor(message2, payload, claim = "unspecified", reason = "unspecified") {
    super(message2, { cause: { claim, reason, payload } });
    this.claim = claim;
    this.reason = reason;
    this.payload = payload;
  }
};
var JWTExpired = class extends JOSEError {
  static {
    __name(this, "JWTExpired");
  }
  static code = "ERR_JWT_EXPIRED";
  code = "ERR_JWT_EXPIRED";
  claim;
  reason;
  payload;
  constructor(message2, payload, claim = "unspecified", reason = "unspecified") {
    super(message2, { cause: { claim, reason, payload } });
    this.claim = claim;
    this.reason = reason;
    this.payload = payload;
  }
};
var JOSEAlgNotAllowed = class extends JOSEError {
  static {
    __name(this, "JOSEAlgNotAllowed");
  }
  static code = "ERR_JOSE_ALG_NOT_ALLOWED";
  code = "ERR_JOSE_ALG_NOT_ALLOWED";
};
var JOSENotSupported = class extends JOSEError {
  static {
    __name(this, "JOSENotSupported");
  }
  static code = "ERR_JOSE_NOT_SUPPORTED";
  code = "ERR_JOSE_NOT_SUPPORTED";
};
var JWSInvalid = class extends JOSEError {
  static {
    __name(this, "JWSInvalid");
  }
  static code = "ERR_JWS_INVALID";
  code = "ERR_JWS_INVALID";
};
var JWTInvalid = class extends JOSEError {
  static {
    __name(this, "JWTInvalid");
  }
  static code = "ERR_JWT_INVALID";
  code = "ERR_JWT_INVALID";
};
var JWKSInvalid = class extends JOSEError {
  static {
    __name(this, "JWKSInvalid");
  }
  static code = "ERR_JWKS_INVALID";
  code = "ERR_JWKS_INVALID";
};
var JWKSNoMatchingKey = class extends JOSEError {
  static {
    __name(this, "JWKSNoMatchingKey");
  }
  static code = "ERR_JWKS_NO_MATCHING_KEY";
  code = "ERR_JWKS_NO_MATCHING_KEY";
  constructor(message2 = "no applicable key found in the JSON Web Key Set", options) {
    super(message2, options);
  }
};
var JWKSMultipleMatchingKeys = class extends JOSEError {
  static {
    __name(this, "JWKSMultipleMatchingKeys");
  }
  [Symbol.asyncIterator];
  static code = "ERR_JWKS_MULTIPLE_MATCHING_KEYS";
  code = "ERR_JWKS_MULTIPLE_MATCHING_KEYS";
  constructor(message2 = "multiple matching keys found in the JSON Web Key Set", options) {
    super(message2, options);
  }
};
var JWKSTimeout = class extends JOSEError {
  static {
    __name(this, "JWKSTimeout");
  }
  static code = "ERR_JWKS_TIMEOUT";
  code = "ERR_JWKS_TIMEOUT";
  constructor(message2 = "request timed out", options) {
    super(message2, options);
  }
};
var JWSSignatureVerificationFailed = class extends JOSEError {
  static {
    __name(this, "JWSSignatureVerificationFailed");
  }
  static code = "ERR_JWS_SIGNATURE_VERIFICATION_FAILED";
  code = "ERR_JWS_SIGNATURE_VERIFICATION_FAILED";
  constructor(message2 = "signature verification failed", options) {
    super(message2, options);
  }
};

// node_modules/jose/dist/webapi/lib/is_key_like.js
var isCryptoKey = /* @__PURE__ */ __name((key) => {
  if (key?.[Symbol.toStringTag] === "CryptoKey")
    return true;
  try {
    return key instanceof CryptoKey;
  } catch {
    return false;
  }
}, "isCryptoKey");
var isKeyObject = /* @__PURE__ */ __name((key) => key?.[Symbol.toStringTag] === "KeyObject", "isKeyObject");
var isKeyLike = /* @__PURE__ */ __name((key) => isCryptoKey(key) || isKeyObject(key), "isKeyLike");

// node_modules/jose/dist/webapi/lib/helpers.js
function assertNotSet(value, name) {
  if (value) {
    throw new TypeError(`${name} can only be called once`);
  }
}
__name(assertNotSet, "assertNotSet");
function decodeBase64url(value, label, ErrorClass) {
  try {
    return decode(value);
  } catch {
    throw new ErrorClass(`Failed to base64url decode the ${label}`);
  }
}
__name(decodeBase64url, "decodeBase64url");

// node_modules/jose/dist/webapi/lib/type_checks.js
var isObjectLike = /* @__PURE__ */ __name((value) => typeof value === "object" && value !== null, "isObjectLike");
function isObject(input) {
  if (!isObjectLike(input) || Object.prototype.toString.call(input) !== "[object Object]") {
    return false;
  }
  if (Object.getPrototypeOf(input) === null) {
    return true;
  }
  let proto = input;
  while (Object.getPrototypeOf(proto) !== null) {
    proto = Object.getPrototypeOf(proto);
  }
  return Object.getPrototypeOf(input) === proto;
}
__name(isObject, "isObject");
function isDisjoint(...headers) {
  const sources = headers.filter(Boolean);
  if (sources.length === 0 || sources.length === 1) {
    return true;
  }
  let acc;
  for (const header of sources) {
    const parameters = Object.keys(header);
    if (!acc || acc.size === 0) {
      acc = new Set(parameters);
      continue;
    }
    for (const parameter of parameters) {
      if (acc.has(parameter)) {
        return false;
      }
      acc.add(parameter);
    }
  }
  return true;
}
__name(isDisjoint, "isDisjoint");
var isJWK = /* @__PURE__ */ __name((key) => isObject(key) && typeof key.kty === "string", "isJWK");
var isPrivateJWK = /* @__PURE__ */ __name((key) => key.kty !== "oct" && (key.kty === "AKP" && typeof key.priv === "string" || typeof key.d === "string"), "isPrivateJWK");
var isPublicJWK = /* @__PURE__ */ __name((key) => key.kty !== "oct" && key.d === void 0 && key.priv === void 0, "isPublicJWK");
var isSecretJWK = /* @__PURE__ */ __name((key) => key.kty === "oct" && typeof key.k === "string", "isSecretJWK");

// node_modules/jose/dist/webapi/lib/signing.js
function checkKeyLength(alg, key) {
  if (alg.startsWith("RS") || alg.startsWith("PS")) {
    const { modulusLength } = key.algorithm;
    if (typeof modulusLength !== "number" || modulusLength < 2048) {
      throw new TypeError(`${alg} requires key modulusLength to be 2048 bits or larger`);
    }
  }
}
__name(checkKeyLength, "checkKeyLength");
function subtleAlgorithm(alg, algorithm) {
  const hash = `SHA-${alg.slice(-3)}`;
  switch (alg) {
    case "HS256":
    case "HS384":
    case "HS512":
      return { hash, name: "HMAC" };
    case "PS256":
    case "PS384":
    case "PS512":
      return { hash, name: "RSA-PSS", saltLength: parseInt(alg.slice(-3), 10) >> 3 };
    case "RS256":
    case "RS384":
    case "RS512":
      return { hash, name: "RSASSA-PKCS1-v1_5" };
    case "ES256":
    case "ES384":
    case "ES512":
      return { hash, name: "ECDSA", namedCurve: algorithm.namedCurve };
    case "Ed25519":
    case "EdDSA":
      return { name: "Ed25519" };
    case "ML-DSA-44":
    case "ML-DSA-65":
    case "ML-DSA-87":
      return { name: alg };
    default:
      throw new JOSENotSupported(`alg ${alg} is not supported either by JOSE or your javascript runtime`);
  }
}
__name(subtleAlgorithm, "subtleAlgorithm");
async function getSigKey(alg, key, usage) {
  if (key instanceof Uint8Array) {
    if (!alg.startsWith("HS")) {
      throw new TypeError(invalidKeyInput(key, "CryptoKey", "KeyObject", "JSON Web Key"));
    }
    return crypto.subtle.importKey("raw", key, { hash: `SHA-${alg.slice(-3)}`, name: "HMAC" }, false, [usage]);
  }
  checkSigCryptoKey(key, alg, usage);
  return key;
}
__name(getSigKey, "getSigKey");
async function sign(alg, key, data) {
  const cryptoKey = await getSigKey(alg, key, "sign");
  checkKeyLength(alg, cryptoKey);
  const signature = await crypto.subtle.sign(subtleAlgorithm(alg, cryptoKey.algorithm), cryptoKey, data);
  return new Uint8Array(signature);
}
__name(sign, "sign");
async function verify(alg, key, signature, data) {
  const cryptoKey = await getSigKey(alg, key, "verify");
  checkKeyLength(alg, cryptoKey);
  const algorithm = subtleAlgorithm(alg, cryptoKey.algorithm);
  try {
    return await crypto.subtle.verify(algorithm, cryptoKey, signature, data);
  } catch {
    return false;
  }
}
__name(verify, "verify");

// node_modules/jose/dist/webapi/lib/jwk_to_key.js
var unsupportedAlg = 'Invalid or unsupported JWK "alg" (Algorithm) Parameter value';
function subtleMapping(jwk) {
  let algorithm;
  let keyUsages;
  switch (jwk.kty) {
    case "AKP": {
      switch (jwk.alg) {
        case "ML-DSA-44":
        case "ML-DSA-65":
        case "ML-DSA-87":
          algorithm = { name: jwk.alg };
          keyUsages = jwk.priv ? ["sign"] : ["verify"];
          break;
        default:
          throw new JOSENotSupported(unsupportedAlg);
      }
      break;
    }
    case "RSA": {
      switch (jwk.alg) {
        case "PS256":
        case "PS384":
        case "PS512":
          algorithm = { name: "RSA-PSS", hash: `SHA-${jwk.alg.slice(-3)}` };
          keyUsages = jwk.d ? ["sign"] : ["verify"];
          break;
        case "RS256":
        case "RS384":
        case "RS512":
          algorithm = { name: "RSASSA-PKCS1-v1_5", hash: `SHA-${jwk.alg.slice(-3)}` };
          keyUsages = jwk.d ? ["sign"] : ["verify"];
          break;
        case "RSA-OAEP":
        case "RSA-OAEP-256":
        case "RSA-OAEP-384":
        case "RSA-OAEP-512":
          algorithm = {
            name: "RSA-OAEP",
            hash: `SHA-${parseInt(jwk.alg.slice(-3), 10) || 1}`
          };
          keyUsages = jwk.d ? ["decrypt", "unwrapKey"] : ["encrypt", "wrapKey"];
          break;
        default:
          throw new JOSENotSupported(unsupportedAlg);
      }
      break;
    }
    case "EC": {
      switch (jwk.alg) {
        case "ES256":
        case "ES384":
        case "ES512":
          algorithm = {
            name: "ECDSA",
            namedCurve: { ES256: "P-256", ES384: "P-384", ES512: "P-521" }[jwk.alg]
          };
          keyUsages = jwk.d ? ["sign"] : ["verify"];
          break;
        case "ECDH-ES":
        case "ECDH-ES+A128KW":
        case "ECDH-ES+A192KW":
        case "ECDH-ES+A256KW":
          algorithm = { name: "ECDH", namedCurve: jwk.crv };
          keyUsages = jwk.d ? ["deriveBits"] : [];
          break;
        default:
          throw new JOSENotSupported(unsupportedAlg);
      }
      break;
    }
    case "OKP": {
      switch (jwk.alg) {
        case "Ed25519":
        case "EdDSA":
          algorithm = { name: "Ed25519" };
          keyUsages = jwk.d ? ["sign"] : ["verify"];
          break;
        case "ECDH-ES":
        case "ECDH-ES+A128KW":
        case "ECDH-ES+A192KW":
        case "ECDH-ES+A256KW":
          algorithm = { name: jwk.crv };
          keyUsages = jwk.d ? ["deriveBits"] : [];
          break;
        default:
          throw new JOSENotSupported(unsupportedAlg);
      }
      break;
    }
    default:
      throw new JOSENotSupported('Invalid or unsupported JWK "kty" (Key Type) Parameter value');
  }
  return { algorithm, keyUsages };
}
__name(subtleMapping, "subtleMapping");
async function jwkToKey(jwk) {
  if (!jwk.alg) {
    throw new TypeError('"alg" argument is required when "jwk.alg" is not present');
  }
  const { algorithm, keyUsages } = subtleMapping(jwk);
  const keyData = { ...jwk };
  if (keyData.kty !== "AKP") {
    delete keyData.alg;
  }
  delete keyData.use;
  return crypto.subtle.importKey("jwk", keyData, algorithm, jwk.ext ?? (jwk.d || jwk.priv ? false : true), jwk.key_ops ?? keyUsages);
}
__name(jwkToKey, "jwkToKey");

// node_modules/jose/dist/webapi/lib/normalize_key.js
var unusableForAlg = "given KeyObject instance cannot be used for this algorithm";
var cache;
var handleJWK = /* @__PURE__ */ __name(async (key, jwk, alg, freeze = false) => {
  cache ||= /* @__PURE__ */ new WeakMap();
  let cached = cache.get(key);
  if (cached?.[alg]) {
    return cached[alg];
  }
  const cryptoKey = await jwkToKey({ ...jwk, alg });
  if (freeze)
    Object.freeze(key);
  if (!cached) {
    cache.set(key, { [alg]: cryptoKey });
  } else {
    cached[alg] = cryptoKey;
  }
  return cryptoKey;
}, "handleJWK");
var handleKeyObject = /* @__PURE__ */ __name((keyObject, alg) => {
  cache ||= /* @__PURE__ */ new WeakMap();
  let cached = cache.get(keyObject);
  if (cached?.[alg]) {
    return cached[alg];
  }
  const isPublic = keyObject.type === "public";
  const extractable = isPublic ? true : false;
  let cryptoKey;
  if (keyObject.asymmetricKeyType === "x25519") {
    switch (alg) {
      case "ECDH-ES":
      case "ECDH-ES+A128KW":
      case "ECDH-ES+A192KW":
      case "ECDH-ES+A256KW":
        break;
      default:
        throw new TypeError(unusableForAlg);
    }
    cryptoKey = keyObject.toCryptoKey(keyObject.asymmetricKeyType, extractable, isPublic ? [] : ["deriveBits"]);
  }
  if (keyObject.asymmetricKeyType === "ed25519") {
    if (alg !== "EdDSA" && alg !== "Ed25519") {
      throw new TypeError(unusableForAlg);
    }
    cryptoKey = keyObject.toCryptoKey(keyObject.asymmetricKeyType, extractable, [
      isPublic ? "verify" : "sign"
    ]);
  }
  switch (keyObject.asymmetricKeyType) {
    case "ml-dsa-44":
    case "ml-dsa-65":
    case "ml-dsa-87": {
      if (alg !== keyObject.asymmetricKeyType.toUpperCase()) {
        throw new TypeError(unusableForAlg);
      }
      cryptoKey = keyObject.toCryptoKey(keyObject.asymmetricKeyType, extractable, [
        isPublic ? "verify" : "sign"
      ]);
    }
  }
  if (keyObject.asymmetricKeyType === "rsa") {
    let hash;
    switch (alg) {
      case "RSA-OAEP":
        hash = "SHA-1";
        break;
      case "RS256":
      case "PS256":
      case "RSA-OAEP-256":
        hash = "SHA-256";
        break;
      case "RS384":
      case "PS384":
      case "RSA-OAEP-384":
        hash = "SHA-384";
        break;
      case "RS512":
      case "PS512":
      case "RSA-OAEP-512":
        hash = "SHA-512";
        break;
      default:
        throw new TypeError(unusableForAlg);
    }
    if (alg.startsWith("RSA-OAEP")) {
      return keyObject.toCryptoKey({
        name: "RSA-OAEP",
        hash
      }, extractable, isPublic ? ["encrypt"] : ["decrypt"]);
    }
    cryptoKey = keyObject.toCryptoKey({
      name: alg.startsWith("PS") ? "RSA-PSS" : "RSASSA-PKCS1-v1_5",
      hash
    }, extractable, [isPublic ? "verify" : "sign"]);
  }
  if (keyObject.asymmetricKeyType === "ec") {
    const nist = /* @__PURE__ */ new Map([
      ["prime256v1", "P-256"],
      ["secp384r1", "P-384"],
      ["secp521r1", "P-521"]
    ]);
    const namedCurve = nist.get(keyObject.asymmetricKeyDetails?.namedCurve);
    if (!namedCurve) {
      throw new TypeError(unusableForAlg);
    }
    const expectedCurve = { ES256: "P-256", ES384: "P-384", ES512: "P-521" };
    if (expectedCurve[alg] && namedCurve === expectedCurve[alg]) {
      cryptoKey = keyObject.toCryptoKey({
        name: "ECDSA",
        namedCurve
      }, extractable, [isPublic ? "verify" : "sign"]);
    }
    if (alg.startsWith("ECDH-ES")) {
      cryptoKey = keyObject.toCryptoKey({
        name: "ECDH",
        namedCurve
      }, extractable, isPublic ? [] : ["deriveBits"]);
    }
  }
  if (!cryptoKey) {
    throw new TypeError(unusableForAlg);
  }
  if (!cached) {
    cache.set(keyObject, { [alg]: cryptoKey });
  } else {
    cached[alg] = cryptoKey;
  }
  return cryptoKey;
}, "handleKeyObject");
async function normalizeKey(key, alg) {
  if (key instanceof Uint8Array) {
    return key;
  }
  if (isCryptoKey(key)) {
    return key;
  }
  if (isKeyObject(key)) {
    if (key.type === "secret") {
      return key.export();
    }
    if ("toCryptoKey" in key && typeof key.toCryptoKey === "function") {
      try {
        return handleKeyObject(key, alg);
      } catch (err) {
        if (err instanceof TypeError) {
          throw err;
        }
      }
    }
    let jwk = key.export({ format: "jwk" });
    return handleJWK(key, jwk, alg);
  }
  if (isJWK(key)) {
    if (key.k) {
      return decode(key.k);
    }
    return handleJWK(key, key, alg, true);
  }
  throw new Error("unreachable");
}
__name(normalizeKey, "normalizeKey");

// node_modules/jose/dist/webapi/lib/asn1.js
var bytesEqual = /* @__PURE__ */ __name((a, b) => {
  if (a.byteLength !== b.length)
    return false;
  for (let i = 0; i < a.byteLength; i++) {
    if (a[i] !== b[i])
      return false;
  }
  return true;
}, "bytesEqual");
var createASN1State = /* @__PURE__ */ __name((data) => ({ data, pos: 0 }), "createASN1State");
var parseLength = /* @__PURE__ */ __name((state) => {
  const first = state.data[state.pos++];
  if (first & 128) {
    const lengthOfLen = first & 127;
    let length = 0;
    for (let i = 0; i < lengthOfLen; i++) {
      length = length << 8 | state.data[state.pos++];
    }
    return length;
  }
  return first;
}, "parseLength");
var expectTag = /* @__PURE__ */ __name((state, expectedTag, errorMessage) => {
  if (state.data[state.pos++] !== expectedTag) {
    throw new Error(errorMessage);
  }
}, "expectTag");
var getSubarray = /* @__PURE__ */ __name((state, length) => {
  const result = state.data.subarray(state.pos, state.pos + length);
  state.pos += length;
  return result;
}, "getSubarray");
var parseAlgorithmOID = /* @__PURE__ */ __name((state) => {
  expectTag(state, 6, "Expected algorithm OID");
  const oidLen = parseLength(state);
  return getSubarray(state, oidLen);
}, "parseAlgorithmOID");
function parsePKCS8Header(state) {
  expectTag(state, 48, "Invalid PKCS#8 structure");
  parseLength(state);
  expectTag(state, 2, "Expected version field");
  const verLen = parseLength(state);
  state.pos += verLen;
  expectTag(state, 48, "Expected algorithm identifier");
  const algIdLen = parseLength(state);
  const algIdStart = state.pos;
  return { algIdStart, algIdLength: algIdLen };
}
__name(parsePKCS8Header, "parsePKCS8Header");
var parseECAlgorithmIdentifier = /* @__PURE__ */ __name((state) => {
  const algOid = parseAlgorithmOID(state);
  if (bytesEqual(algOid, [43, 101, 110])) {
    return "X25519";
  }
  if (!bytesEqual(algOid, [42, 134, 72, 206, 61, 2, 1])) {
    throw new Error("Unsupported key algorithm");
  }
  expectTag(state, 6, "Expected curve OID");
  const curveOidLen = parseLength(state);
  const curveOid = getSubarray(state, curveOidLen);
  for (const { name, oid } of [
    { name: "P-256", oid: [42, 134, 72, 206, 61, 3, 1, 7] },
    { name: "P-384", oid: [43, 129, 4, 0, 34] },
    { name: "P-521", oid: [43, 129, 4, 0, 35] }
  ]) {
    if (bytesEqual(curveOid, oid)) {
      return name;
    }
  }
  throw new Error("Unsupported named curve");
}, "parseECAlgorithmIdentifier");
var genericImport = /* @__PURE__ */ __name(async (keyFormat, keyData, alg, options) => {
  let algorithm;
  let keyUsages;
  const isPublic = keyFormat === "spki";
  const getSigUsages = /* @__PURE__ */ __name(() => isPublic ? ["verify"] : ["sign"], "getSigUsages");
  const getEncUsages = /* @__PURE__ */ __name(() => isPublic ? ["encrypt", "wrapKey"] : ["decrypt", "unwrapKey"], "getEncUsages");
  switch (alg) {
    case "PS256":
    case "PS384":
    case "PS512":
      algorithm = { name: "RSA-PSS", hash: `SHA-${alg.slice(-3)}` };
      keyUsages = getSigUsages();
      break;
    case "RS256":
    case "RS384":
    case "RS512":
      algorithm = { name: "RSASSA-PKCS1-v1_5", hash: `SHA-${alg.slice(-3)}` };
      keyUsages = getSigUsages();
      break;
    case "RSA-OAEP":
    case "RSA-OAEP-256":
    case "RSA-OAEP-384":
    case "RSA-OAEP-512":
      algorithm = {
        name: "RSA-OAEP",
        hash: `SHA-${parseInt(alg.slice(-3), 10) || 1}`
      };
      keyUsages = getEncUsages();
      break;
    case "ES256":
    case "ES384":
    case "ES512": {
      const curveMap = { ES256: "P-256", ES384: "P-384", ES512: "P-521" };
      algorithm = { name: "ECDSA", namedCurve: curveMap[alg] };
      keyUsages = getSigUsages();
      break;
    }
    case "ECDH-ES":
    case "ECDH-ES+A128KW":
    case "ECDH-ES+A192KW":
    case "ECDH-ES+A256KW": {
      try {
        const namedCurve = options.getNamedCurve(keyData);
        algorithm = namedCurve === "X25519" ? { name: "X25519" } : { name: "ECDH", namedCurve };
      } catch (cause) {
        throw new JOSENotSupported("Invalid or unsupported key format");
      }
      keyUsages = isPublic ? [] : ["deriveBits"];
      break;
    }
    case "Ed25519":
    case "EdDSA":
      algorithm = { name: "Ed25519" };
      keyUsages = getSigUsages();
      break;
    case "ML-DSA-44":
    case "ML-DSA-65":
    case "ML-DSA-87":
      algorithm = { name: alg };
      keyUsages = getSigUsages();
      break;
    default:
      throw new JOSENotSupported('Invalid or unsupported "alg" (Algorithm) value');
  }
  return crypto.subtle.importKey(keyFormat, keyData, algorithm, options?.extractable ?? (isPublic ? true : false), keyUsages);
}, "genericImport");
var processPEMData = /* @__PURE__ */ __name((pem, pattern) => {
  return decodeBase64(pem.replace(pattern, ""));
}, "processPEMData");
var fromPKCS8 = /* @__PURE__ */ __name((pem, alg, options) => {
  const keyData = processPEMData(pem, /(?:-----(?:BEGIN|END) PRIVATE KEY-----|\s)/g);
  let opts = options;
  if (alg?.startsWith?.("ECDH-ES")) {
    opts ||= {};
    opts.getNamedCurve = (keyData2) => {
      const state = createASN1State(keyData2);
      parsePKCS8Header(state);
      return parseECAlgorithmIdentifier(state);
    };
  }
  return genericImport("pkcs8", keyData, alg, opts);
}, "fromPKCS8");

// node_modules/jose/dist/webapi/key/import.js
async function importPKCS8(pkcs8, alg, options) {
  if (typeof pkcs8 !== "string" || pkcs8.indexOf("-----BEGIN PRIVATE KEY-----") !== 0) {
    throw new TypeError('"pkcs8" must be PKCS#8 formatted string');
  }
  return fromPKCS8(pkcs8, alg, options);
}
__name(importPKCS8, "importPKCS8");
async function importJWK(jwk, alg, options) {
  if (!isObject(jwk)) {
    throw new TypeError("JWK must be an object");
  }
  let ext;
  alg ??= jwk.alg;
  ext ??= options?.extractable ?? jwk.ext;
  switch (jwk.kty) {
    case "oct":
      if (typeof jwk.k !== "string" || !jwk.k) {
        throw new TypeError('missing "k" (Key Value) Parameter value');
      }
      return decode(jwk.k);
    case "RSA":
      if ("oth" in jwk && jwk.oth !== void 0) {
        throw new JOSENotSupported('RSA JWK "oth" (Other Primes Info) Parameter value is not supported');
      }
      return jwkToKey({ ...jwk, alg, ext });
    case "AKP": {
      if (typeof jwk.alg !== "string" || !jwk.alg) {
        throw new TypeError('missing "alg" (Algorithm) Parameter value');
      }
      if (alg !== void 0 && alg !== jwk.alg) {
        throw new TypeError("JWK alg and alg option value mismatch");
      }
      return jwkToKey({ ...jwk, ext });
    }
    case "EC":
    case "OKP":
      return jwkToKey({ ...jwk, alg, ext });
    default:
      throw new JOSENotSupported('Unsupported "kty" (Key Type) Parameter value');
  }
}
__name(importJWK, "importJWK");

// node_modules/jose/dist/webapi/lib/validate_crit.js
function validateCrit(Err, recognizedDefault, recognizedOption, protectedHeader, joseHeader) {
  if (joseHeader.crit !== void 0 && protectedHeader?.crit === void 0) {
    throw new Err('"crit" (Critical) Header Parameter MUST be integrity protected');
  }
  if (!protectedHeader || protectedHeader.crit === void 0) {
    return /* @__PURE__ */ new Set();
  }
  if (!Array.isArray(protectedHeader.crit) || protectedHeader.crit.length === 0 || protectedHeader.crit.some((input) => typeof input !== "string" || input.length === 0)) {
    throw new Err('"crit" (Critical) Header Parameter MUST be an array of non-empty strings when present');
  }
  let recognized;
  if (recognizedOption !== void 0) {
    recognized = new Map([...Object.entries(recognizedOption), ...recognizedDefault.entries()]);
  } else {
    recognized = recognizedDefault;
  }
  for (const parameter of protectedHeader.crit) {
    if (!recognized.has(parameter)) {
      throw new JOSENotSupported(`Extension Header Parameter "${parameter}" is not recognized`);
    }
    if (joseHeader[parameter] === void 0) {
      throw new Err(`Extension Header Parameter "${parameter}" is missing`);
    }
    if (recognized.get(parameter) && protectedHeader[parameter] === void 0) {
      throw new Err(`Extension Header Parameter "${parameter}" MUST be integrity protected`);
    }
  }
  return new Set(protectedHeader.crit);
}
__name(validateCrit, "validateCrit");

// node_modules/jose/dist/webapi/lib/validate_algorithms.js
function validateAlgorithms(option, algorithms) {
  if (algorithms !== void 0 && (!Array.isArray(algorithms) || algorithms.some((s) => typeof s !== "string"))) {
    throw new TypeError(`"${option}" option must be an array of strings`);
  }
  if (!algorithms) {
    return void 0;
  }
  return new Set(algorithms);
}
__name(validateAlgorithms, "validateAlgorithms");

// node_modules/jose/dist/webapi/lib/check_key_type.js
var tag = /* @__PURE__ */ __name((key) => key?.[Symbol.toStringTag], "tag");
var jwkMatchesOp = /* @__PURE__ */ __name((alg, key, usage) => {
  if (key.use !== void 0) {
    let expected;
    switch (usage) {
      case "sign":
      case "verify":
        expected = "sig";
        break;
      case "encrypt":
      case "decrypt":
        expected = "enc";
        break;
    }
    if (key.use !== expected) {
      throw new TypeError(`Invalid key for this operation, its "use" must be "${expected}" when present`);
    }
  }
  if (key.alg !== void 0 && key.alg !== alg) {
    throw new TypeError(`Invalid key for this operation, its "alg" must be "${alg}" when present`);
  }
  if (Array.isArray(key.key_ops)) {
    let expectedKeyOp;
    switch (true) {
      case (usage === "sign" || usage === "verify"):
      case alg === "dir":
      case alg.includes("CBC-HS"):
        expectedKeyOp = usage;
        break;
      case alg.startsWith("PBES2"):
        expectedKeyOp = "deriveBits";
        break;
      case /^A\d{3}(?:GCM)?(?:KW)?$/.test(alg):
        if (!alg.includes("GCM") && alg.endsWith("KW")) {
          expectedKeyOp = usage === "encrypt" ? "wrapKey" : "unwrapKey";
        } else {
          expectedKeyOp = usage;
        }
        break;
      case (usage === "encrypt" && alg.startsWith("RSA")):
        expectedKeyOp = "wrapKey";
        break;
      case usage === "decrypt":
        expectedKeyOp = alg.startsWith("RSA") ? "unwrapKey" : "deriveBits";
        break;
    }
    if (expectedKeyOp && key.key_ops?.includes?.(expectedKeyOp) === false) {
      throw new TypeError(`Invalid key for this operation, its "key_ops" must include "${expectedKeyOp}" when present`);
    }
  }
  return true;
}, "jwkMatchesOp");
var symmetricTypeCheck = /* @__PURE__ */ __name((alg, key, usage) => {
  if (key instanceof Uint8Array)
    return;
  if (isJWK(key)) {
    if (isSecretJWK(key) && jwkMatchesOp(alg, key, usage))
      return;
    throw new TypeError(`JSON Web Key for symmetric algorithms must have JWK "kty" (Key Type) equal to "oct" and the JWK "k" (Key Value) present`);
  }
  if (!isKeyLike(key)) {
    throw new TypeError(withAlg(alg, key, "CryptoKey", "KeyObject", "JSON Web Key", "Uint8Array"));
  }
  if (key.type !== "secret") {
    throw new TypeError(`${tag(key)} instances for symmetric algorithms must be of type "secret"`);
  }
}, "symmetricTypeCheck");
var asymmetricTypeCheck = /* @__PURE__ */ __name((alg, key, usage) => {
  if (isJWK(key)) {
    switch (usage) {
      case "decrypt":
      case "sign":
        if (isPrivateJWK(key) && jwkMatchesOp(alg, key, usage))
          return;
        throw new TypeError(`JSON Web Key for this operation must be a private JWK`);
      case "encrypt":
      case "verify":
        if (isPublicJWK(key) && jwkMatchesOp(alg, key, usage))
          return;
        throw new TypeError(`JSON Web Key for this operation must be a public JWK`);
    }
  }
  if (!isKeyLike(key)) {
    throw new TypeError(withAlg(alg, key, "CryptoKey", "KeyObject", "JSON Web Key"));
  }
  if (key.type === "secret") {
    throw new TypeError(`${tag(key)} instances for asymmetric algorithms must not be of type "secret"`);
  }
  if (key.type === "public") {
    switch (usage) {
      case "sign":
        throw new TypeError(`${tag(key)} instances for asymmetric algorithm signing must be of type "private"`);
      case "decrypt":
        throw new TypeError(`${tag(key)} instances for asymmetric algorithm decryption must be of type "private"`);
    }
  }
  if (key.type === "private") {
    switch (usage) {
      case "verify":
        throw new TypeError(`${tag(key)} instances for asymmetric algorithm verifying must be of type "public"`);
      case "encrypt":
        throw new TypeError(`${tag(key)} instances for asymmetric algorithm encryption must be of type "public"`);
    }
  }
}, "asymmetricTypeCheck");
function checkKeyType(alg, key, usage) {
  switch (alg.substring(0, 2)) {
    case "A1":
    case "A2":
    case "di":
    case "HS":
    case "PB":
      symmetricTypeCheck(alg, key, usage);
      break;
    default:
      asymmetricTypeCheck(alg, key, usage);
  }
}
__name(checkKeyType, "checkKeyType");

// node_modules/jose/dist/webapi/jws/flattened/verify.js
async function flattenedVerify(jws, key, options) {
  if (!isObject(jws)) {
    throw new JWSInvalid("Flattened JWS must be an object");
  }
  if (jws.protected === void 0 && jws.header === void 0) {
    throw new JWSInvalid('Flattened JWS must have either of the "protected" or "header" members');
  }
  if (jws.protected !== void 0 && typeof jws.protected !== "string") {
    throw new JWSInvalid("JWS Protected Header incorrect type");
  }
  if (jws.payload === void 0) {
    throw new JWSInvalid("JWS Payload missing");
  }
  if (typeof jws.signature !== "string") {
    throw new JWSInvalid("JWS Signature missing or incorrect type");
  }
  if (jws.header !== void 0 && !isObject(jws.header)) {
    throw new JWSInvalid("JWS Unprotected Header incorrect type");
  }
  let parsedProt = {};
  if (jws.protected) {
    try {
      const protectedHeader = decode(jws.protected);
      parsedProt = JSON.parse(decoder.decode(protectedHeader));
    } catch {
      throw new JWSInvalid("JWS Protected Header is invalid");
    }
  }
  if (!isDisjoint(parsedProt, jws.header)) {
    throw new JWSInvalid("JWS Protected and JWS Unprotected Header Parameter names must be disjoint");
  }
  const joseHeader = {
    ...parsedProt,
    ...jws.header
  };
  const extensions = validateCrit(JWSInvalid, /* @__PURE__ */ new Map([["b64", true]]), options?.crit, parsedProt, joseHeader);
  let b64 = true;
  if (extensions.has("b64")) {
    b64 = parsedProt.b64;
    if (typeof b64 !== "boolean") {
      throw new JWSInvalid('The "b64" (base64url-encode payload) Header Parameter must be a boolean');
    }
  }
  const { alg } = joseHeader;
  if (typeof alg !== "string" || !alg) {
    throw new JWSInvalid('JWS "alg" (Algorithm) Header Parameter missing or invalid');
  }
  const algorithms = options && validateAlgorithms("algorithms", options.algorithms);
  if (algorithms && !algorithms.has(alg)) {
    throw new JOSEAlgNotAllowed('"alg" (Algorithm) Header Parameter value not allowed');
  }
  if (b64) {
    if (typeof jws.payload !== "string") {
      throw new JWSInvalid("JWS Payload must be a string");
    }
  } else if (typeof jws.payload !== "string" && !(jws.payload instanceof Uint8Array)) {
    throw new JWSInvalid("JWS Payload must be a string or an Uint8Array instance");
  }
  let resolvedKey = false;
  if (typeof key === "function") {
    key = await key(parsedProt, jws);
    resolvedKey = true;
  }
  checkKeyType(alg, key, "verify");
  const data = concat(jws.protected !== void 0 ? encode(jws.protected) : new Uint8Array(), encode("."), typeof jws.payload === "string" ? b64 ? encode(jws.payload) : encoder.encode(jws.payload) : jws.payload);
  const signature = decodeBase64url(jws.signature, "signature", JWSInvalid);
  const k = await normalizeKey(key, alg);
  const verified = await verify(alg, k, signature, data);
  if (!verified) {
    throw new JWSSignatureVerificationFailed();
  }
  let payload;
  if (b64) {
    payload = decodeBase64url(jws.payload, "payload", JWSInvalid);
  } else if (typeof jws.payload === "string") {
    payload = encoder.encode(jws.payload);
  } else {
    payload = jws.payload;
  }
  const result = { payload };
  if (jws.protected !== void 0) {
    result.protectedHeader = parsedProt;
  }
  if (jws.header !== void 0) {
    result.unprotectedHeader = jws.header;
  }
  if (resolvedKey) {
    return { ...result, key: k };
  }
  return result;
}
__name(flattenedVerify, "flattenedVerify");

// node_modules/jose/dist/webapi/jws/compact/verify.js
async function compactVerify(jws, key, options) {
  if (jws instanceof Uint8Array) {
    jws = decoder.decode(jws);
  }
  if (typeof jws !== "string") {
    throw new JWSInvalid("Compact JWS must be a string or Uint8Array");
  }
  const { 0: protectedHeader, 1: payload, 2: signature, length } = jws.split(".");
  if (length !== 3) {
    throw new JWSInvalid("Invalid Compact JWS");
  }
  const verified = await flattenedVerify({ payload, protected: protectedHeader, signature }, key, options);
  const result = { payload: verified.payload, protectedHeader: verified.protectedHeader };
  if (typeof key === "function") {
    return { ...result, key: verified.key };
  }
  return result;
}
__name(compactVerify, "compactVerify");

// node_modules/jose/dist/webapi/lib/jwt_claims_set.js
var epoch = /* @__PURE__ */ __name((date) => Math.floor(date.getTime() / 1e3), "epoch");
var minute = 60;
var hour = minute * 60;
var day = hour * 24;
var week = day * 7;
var year = day * 365.25;
var REGEX = /^(\+|\-)? ?(\d+|\d+\.\d+) ?(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|w|years?|yrs?|y)(?: (ago|from now))?$/i;
function secs(str) {
  const matched = REGEX.exec(str);
  if (!matched || matched[4] && matched[1]) {
    throw new TypeError("Invalid time period format");
  }
  const value = parseFloat(matched[2]);
  const unit = matched[3].toLowerCase();
  let numericDate;
  switch (unit) {
    case "sec":
    case "secs":
    case "second":
    case "seconds":
    case "s":
      numericDate = Math.round(value);
      break;
    case "minute":
    case "minutes":
    case "min":
    case "mins":
    case "m":
      numericDate = Math.round(value * minute);
      break;
    case "hour":
    case "hours":
    case "hr":
    case "hrs":
    case "h":
      numericDate = Math.round(value * hour);
      break;
    case "day":
    case "days":
    case "d":
      numericDate = Math.round(value * day);
      break;
    case "week":
    case "weeks":
    case "w":
      numericDate = Math.round(value * week);
      break;
    default:
      numericDate = Math.round(value * year);
      break;
  }
  if (matched[1] === "-" || matched[4] === "ago") {
    return -numericDate;
  }
  return numericDate;
}
__name(secs, "secs");
function validateInput(label, input) {
  if (!Number.isFinite(input)) {
    throw new TypeError(`Invalid ${label} input`);
  }
  return input;
}
__name(validateInput, "validateInput");
var normalizeTyp = /* @__PURE__ */ __name((value) => {
  if (value.includes("/")) {
    return value.toLowerCase();
  }
  return `application/${value.toLowerCase()}`;
}, "normalizeTyp");
var checkAudiencePresence = /* @__PURE__ */ __name((audPayload, audOption) => {
  if (typeof audPayload === "string") {
    return audOption.includes(audPayload);
  }
  if (Array.isArray(audPayload)) {
    return audOption.some(Set.prototype.has.bind(new Set(audPayload)));
  }
  return false;
}, "checkAudiencePresence");
function validateClaimsSet(protectedHeader, encodedPayload, options = {}) {
  let payload;
  try {
    payload = JSON.parse(decoder.decode(encodedPayload));
  } catch {
  }
  if (!isObject(payload)) {
    throw new JWTInvalid("JWT Claims Set must be a top-level JSON object");
  }
  const { typ } = options;
  if (typ && (typeof protectedHeader.typ !== "string" || normalizeTyp(protectedHeader.typ) !== normalizeTyp(typ))) {
    throw new JWTClaimValidationFailed('unexpected "typ" JWT header value', payload, "typ", "check_failed");
  }
  const { requiredClaims = [], issuer, subject, audience, maxTokenAge } = options;
  const presenceCheck = [...requiredClaims];
  if (maxTokenAge !== void 0)
    presenceCheck.push("iat");
  if (audience !== void 0)
    presenceCheck.push("aud");
  if (subject !== void 0)
    presenceCheck.push("sub");
  if (issuer !== void 0)
    presenceCheck.push("iss");
  for (const claim of new Set(presenceCheck.reverse())) {
    if (!(claim in payload)) {
      throw new JWTClaimValidationFailed(`missing required "${claim}" claim`, payload, claim, "missing");
    }
  }
  if (issuer && !(Array.isArray(issuer) ? issuer : [issuer]).includes(payload.iss)) {
    throw new JWTClaimValidationFailed('unexpected "iss" claim value', payload, "iss", "check_failed");
  }
  if (subject && payload.sub !== subject) {
    throw new JWTClaimValidationFailed('unexpected "sub" claim value', payload, "sub", "check_failed");
  }
  if (audience && !checkAudiencePresence(payload.aud, typeof audience === "string" ? [audience] : audience)) {
    throw new JWTClaimValidationFailed('unexpected "aud" claim value', payload, "aud", "check_failed");
  }
  let tolerance;
  switch (typeof options.clockTolerance) {
    case "string":
      tolerance = secs(options.clockTolerance);
      break;
    case "number":
      tolerance = options.clockTolerance;
      break;
    case "undefined":
      tolerance = 0;
      break;
    default:
      throw new TypeError("Invalid clockTolerance option type");
  }
  const { currentDate } = options;
  const now = epoch(currentDate || /* @__PURE__ */ new Date());
  if ((payload.iat !== void 0 || maxTokenAge) && typeof payload.iat !== "number") {
    throw new JWTClaimValidationFailed('"iat" claim must be a number', payload, "iat", "invalid");
  }
  if (payload.nbf !== void 0) {
    if (typeof payload.nbf !== "number") {
      throw new JWTClaimValidationFailed('"nbf" claim must be a number', payload, "nbf", "invalid");
    }
    if (payload.nbf > now + tolerance) {
      throw new JWTClaimValidationFailed('"nbf" claim timestamp check failed', payload, "nbf", "check_failed");
    }
  }
  if (payload.exp !== void 0) {
    if (typeof payload.exp !== "number") {
      throw new JWTClaimValidationFailed('"exp" claim must be a number', payload, "exp", "invalid");
    }
    if (payload.exp <= now - tolerance) {
      throw new JWTExpired('"exp" claim timestamp check failed', payload, "exp", "check_failed");
    }
  }
  if (maxTokenAge) {
    const age = now - payload.iat;
    const max = typeof maxTokenAge === "number" ? maxTokenAge : secs(maxTokenAge);
    if (age - tolerance > max) {
      throw new JWTExpired('"iat" claim timestamp check failed (too far in the past)', payload, "iat", "check_failed");
    }
    if (age < 0 - tolerance) {
      throw new JWTClaimValidationFailed('"iat" claim timestamp check failed (it should be in the past)', payload, "iat", "check_failed");
    }
  }
  return payload;
}
__name(validateClaimsSet, "validateClaimsSet");
var JWTClaimsBuilder = class {
  static {
    __name(this, "JWTClaimsBuilder");
  }
  #payload;
  constructor(payload) {
    if (!isObject(payload)) {
      throw new TypeError("JWT Claims Set MUST be an object");
    }
    this.#payload = structuredClone(payload);
  }
  data() {
    return encoder.encode(JSON.stringify(this.#payload));
  }
  get iss() {
    return this.#payload.iss;
  }
  set iss(value) {
    this.#payload.iss = value;
  }
  get sub() {
    return this.#payload.sub;
  }
  set sub(value) {
    this.#payload.sub = value;
  }
  get aud() {
    return this.#payload.aud;
  }
  set aud(value) {
    this.#payload.aud = value;
  }
  set jti(value) {
    this.#payload.jti = value;
  }
  set nbf(value) {
    if (typeof value === "number") {
      this.#payload.nbf = validateInput("setNotBefore", value);
    } else if (value instanceof Date) {
      this.#payload.nbf = validateInput("setNotBefore", epoch(value));
    } else {
      this.#payload.nbf = epoch(/* @__PURE__ */ new Date()) + secs(value);
    }
  }
  set exp(value) {
    if (typeof value === "number") {
      this.#payload.exp = validateInput("setExpirationTime", value);
    } else if (value instanceof Date) {
      this.#payload.exp = validateInput("setExpirationTime", epoch(value));
    } else {
      this.#payload.exp = epoch(/* @__PURE__ */ new Date()) + secs(value);
    }
  }
  set iat(value) {
    if (value === void 0) {
      this.#payload.iat = epoch(/* @__PURE__ */ new Date());
    } else if (value instanceof Date) {
      this.#payload.iat = validateInput("setIssuedAt", epoch(value));
    } else if (typeof value === "string") {
      this.#payload.iat = validateInput("setIssuedAt", epoch(/* @__PURE__ */ new Date()) + secs(value));
    } else {
      this.#payload.iat = validateInput("setIssuedAt", value);
    }
  }
};

// node_modules/jose/dist/webapi/jwt/verify.js
async function jwtVerify(jwt, key, options) {
  const verified = await compactVerify(jwt, key, options);
  if (verified.protectedHeader.crit?.includes("b64") && verified.protectedHeader.b64 === false) {
    throw new JWTInvalid("JWTs MUST NOT use unencoded payload");
  }
  const payload = validateClaimsSet(verified.protectedHeader, verified.payload, options);
  const result = { payload, protectedHeader: verified.protectedHeader };
  if (typeof key === "function") {
    return { ...result, key: verified.key };
  }
  return result;
}
__name(jwtVerify, "jwtVerify");

// node_modules/jose/dist/webapi/jws/flattened/sign.js
var FlattenedSign = class {
  static {
    __name(this, "FlattenedSign");
  }
  #payload;
  #protectedHeader;
  #unprotectedHeader;
  constructor(payload) {
    if (!(payload instanceof Uint8Array)) {
      throw new TypeError("payload must be an instance of Uint8Array");
    }
    this.#payload = payload;
  }
  setProtectedHeader(protectedHeader) {
    assertNotSet(this.#protectedHeader, "setProtectedHeader");
    this.#protectedHeader = protectedHeader;
    return this;
  }
  setUnprotectedHeader(unprotectedHeader) {
    assertNotSet(this.#unprotectedHeader, "setUnprotectedHeader");
    this.#unprotectedHeader = unprotectedHeader;
    return this;
  }
  async sign(key, options) {
    if (!this.#protectedHeader && !this.#unprotectedHeader) {
      throw new JWSInvalid("either setProtectedHeader or setUnprotectedHeader must be called before #sign()");
    }
    if (!isDisjoint(this.#protectedHeader, this.#unprotectedHeader)) {
      throw new JWSInvalid("JWS Protected and JWS Unprotected Header Parameter names must be disjoint");
    }
    const joseHeader = {
      ...this.#protectedHeader,
      ...this.#unprotectedHeader
    };
    const extensions = validateCrit(JWSInvalid, /* @__PURE__ */ new Map([["b64", true]]), options?.crit, this.#protectedHeader, joseHeader);
    let b64 = true;
    if (extensions.has("b64")) {
      b64 = this.#protectedHeader.b64;
      if (typeof b64 !== "boolean") {
        throw new JWSInvalid('The "b64" (base64url-encode payload) Header Parameter must be a boolean');
      }
    }
    const { alg } = joseHeader;
    if (typeof alg !== "string" || !alg) {
      throw new JWSInvalid('JWS "alg" (Algorithm) Header Parameter missing or invalid');
    }
    checkKeyType(alg, key, "sign");
    let payloadS;
    let payloadB;
    if (b64) {
      payloadS = encode2(this.#payload);
      payloadB = encode(payloadS);
    } else {
      payloadB = this.#payload;
      payloadS = "";
    }
    let protectedHeaderString;
    let protectedHeaderBytes;
    if (this.#protectedHeader) {
      protectedHeaderString = encode2(JSON.stringify(this.#protectedHeader));
      protectedHeaderBytes = encode(protectedHeaderString);
    } else {
      protectedHeaderString = "";
      protectedHeaderBytes = new Uint8Array();
    }
    const data = concat(protectedHeaderBytes, encode("."), payloadB);
    const k = await normalizeKey(key, alg);
    const signature = await sign(alg, k, data);
    const jws = {
      signature: encode2(signature),
      payload: payloadS
    };
    if (this.#unprotectedHeader) {
      jws.header = this.#unprotectedHeader;
    }
    if (this.#protectedHeader) {
      jws.protected = protectedHeaderString;
    }
    return jws;
  }
};

// node_modules/jose/dist/webapi/jws/compact/sign.js
var CompactSign = class {
  static {
    __name(this, "CompactSign");
  }
  #flattened;
  constructor(payload) {
    this.#flattened = new FlattenedSign(payload);
  }
  setProtectedHeader(protectedHeader) {
    this.#flattened.setProtectedHeader(protectedHeader);
    return this;
  }
  async sign(key, options) {
    const jws = await this.#flattened.sign(key, options);
    if (jws.payload === void 0) {
      throw new TypeError("use the flattened module for creating JWS with b64: false");
    }
    return `${jws.protected}.${jws.payload}.${jws.signature}`;
  }
};

// node_modules/jose/dist/webapi/jwt/sign.js
var SignJWT = class {
  static {
    __name(this, "SignJWT");
  }
  #protectedHeader;
  #jwt;
  constructor(payload = {}) {
    this.#jwt = new JWTClaimsBuilder(payload);
  }
  setIssuer(issuer) {
    this.#jwt.iss = issuer;
    return this;
  }
  setSubject(subject) {
    this.#jwt.sub = subject;
    return this;
  }
  setAudience(audience) {
    this.#jwt.aud = audience;
    return this;
  }
  setJti(jwtId) {
    this.#jwt.jti = jwtId;
    return this;
  }
  setNotBefore(input) {
    this.#jwt.nbf = input;
    return this;
  }
  setExpirationTime(input) {
    this.#jwt.exp = input;
    return this;
  }
  setIssuedAt(input) {
    this.#jwt.iat = input;
    return this;
  }
  setProtectedHeader(protectedHeader) {
    this.#protectedHeader = protectedHeader;
    return this;
  }
  async sign(key, options) {
    const sig = new CompactSign(this.#jwt.data());
    sig.setProtectedHeader(this.#protectedHeader);
    if (Array.isArray(this.#protectedHeader?.crit) && this.#protectedHeader.crit.includes("b64") && this.#protectedHeader.b64 === false) {
      throw new JWTInvalid("JWTs MUST NOT use unencoded payload");
    }
    return sig.sign(key, options);
  }
};

// node_modules/jose/dist/webapi/jwks/local.js
function getKtyFromAlg(alg) {
  switch (typeof alg === "string" && alg.slice(0, 2)) {
    case "RS":
    case "PS":
      return "RSA";
    case "ES":
      return "EC";
    case "Ed":
      return "OKP";
    case "ML":
      return "AKP";
    default:
      throw new JOSENotSupported('Unsupported "alg" value for a JSON Web Key Set');
  }
}
__name(getKtyFromAlg, "getKtyFromAlg");
function isJWKSLike(jwks) {
  return jwks && typeof jwks === "object" && Array.isArray(jwks.keys) && jwks.keys.every(isJWKLike);
}
__name(isJWKSLike, "isJWKSLike");
function isJWKLike(key) {
  return isObject(key);
}
__name(isJWKLike, "isJWKLike");
var LocalJWKSet = class {
  static {
    __name(this, "LocalJWKSet");
  }
  #jwks;
  #cached = /* @__PURE__ */ new WeakMap();
  constructor(jwks) {
    if (!isJWKSLike(jwks)) {
      throw new JWKSInvalid("JSON Web Key Set malformed");
    }
    this.#jwks = structuredClone(jwks);
  }
  jwks() {
    return this.#jwks;
  }
  async getKey(protectedHeader, token) {
    const { alg, kid } = { ...protectedHeader, ...token?.header };
    const kty = getKtyFromAlg(alg);
    const candidates = this.#jwks.keys.filter((jwk2) => {
      let candidate = kty === jwk2.kty;
      if (candidate && typeof kid === "string") {
        candidate = kid === jwk2.kid;
      }
      if (candidate && (typeof jwk2.alg === "string" || kty === "AKP")) {
        candidate = alg === jwk2.alg;
      }
      if (candidate && typeof jwk2.use === "string") {
        candidate = jwk2.use === "sig";
      }
      if (candidate && Array.isArray(jwk2.key_ops)) {
        candidate = jwk2.key_ops.includes("verify");
      }
      if (candidate) {
        switch (alg) {
          case "ES256":
            candidate = jwk2.crv === "P-256";
            break;
          case "ES384":
            candidate = jwk2.crv === "P-384";
            break;
          case "ES512":
            candidate = jwk2.crv === "P-521";
            break;
          case "Ed25519":
          case "EdDSA":
            candidate = jwk2.crv === "Ed25519";
            break;
        }
      }
      return candidate;
    });
    const { 0: jwk, length } = candidates;
    if (length === 0) {
      throw new JWKSNoMatchingKey();
    }
    if (length !== 1) {
      const error = new JWKSMultipleMatchingKeys();
      const _cached = this.#cached;
      error[Symbol.asyncIterator] = async function* () {
        for (const jwk2 of candidates) {
          try {
            yield await importWithAlgCache(_cached, jwk2, alg);
          } catch {
          }
        }
      };
      throw error;
    }
    return importWithAlgCache(this.#cached, jwk, alg);
  }
};
async function importWithAlgCache(cache2, jwk, alg) {
  const cached = cache2.get(jwk) || cache2.set(jwk, {}).get(jwk);
  if (cached[alg] === void 0) {
    const key = await importJWK({ ...jwk, ext: true }, alg);
    if (key instanceof Uint8Array || key.type !== "public") {
      throw new JWKSInvalid("JSON Web Key Set members must be public keys");
    }
    cached[alg] = key;
  }
  return cached[alg];
}
__name(importWithAlgCache, "importWithAlgCache");
function createLocalJWKSet(jwks) {
  const set = new LocalJWKSet(jwks);
  const localJWKSet = /* @__PURE__ */ __name(async (protectedHeader, token) => set.getKey(protectedHeader, token), "localJWKSet");
  Object.defineProperties(localJWKSet, {
    jwks: {
      value: /* @__PURE__ */ __name(() => structuredClone(set.jwks()), "value"),
      enumerable: false,
      configurable: false,
      writable: false
    }
  });
  return localJWKSet;
}
__name(createLocalJWKSet, "createLocalJWKSet");

// node_modules/jose/dist/webapi/jwks/remote.js
function isCloudflareWorkers() {
  return typeof WebSocketPair !== "undefined" || typeof navigator !== "undefined" && true || typeof EdgeRuntime !== "undefined" && EdgeRuntime === "vercel";
}
__name(isCloudflareWorkers, "isCloudflareWorkers");
var USER_AGENT;
if (typeof navigator === "undefined" || !"Cloudflare-Workers"?.startsWith?.("Mozilla/5.0 ")) {
  const NAME = "jose";
  const VERSION = "v6.2.1";
  USER_AGENT = `${NAME}/${VERSION}`;
}
var customFetch = /* @__PURE__ */ Symbol();
async function fetchJwks(url, headers, signal, fetchImpl = fetch) {
  const response = await fetchImpl(url, {
    method: "GET",
    signal,
    redirect: "manual",
    headers
  }).catch((err) => {
    if (err.name === "TimeoutError") {
      throw new JWKSTimeout();
    }
    throw err;
  });
  if (response.status !== 200) {
    throw new JOSEError("Expected 200 OK from the JSON Web Key Set HTTP response");
  }
  try {
    return await response.json();
  } catch {
    throw new JOSEError("Failed to parse the JSON Web Key Set HTTP response as JSON");
  }
}
__name(fetchJwks, "fetchJwks");
var jwksCache = /* @__PURE__ */ Symbol();
function isFreshJwksCache(input, cacheMaxAge) {
  if (typeof input !== "object" || input === null) {
    return false;
  }
  if (!("uat" in input) || typeof input.uat !== "number" || Date.now() - input.uat >= cacheMaxAge) {
    return false;
  }
  if (!("jwks" in input) || !isObject(input.jwks) || !Array.isArray(input.jwks.keys) || !Array.prototype.every.call(input.jwks.keys, isObject)) {
    return false;
  }
  return true;
}
__name(isFreshJwksCache, "isFreshJwksCache");
var RemoteJWKSet = class {
  static {
    __name(this, "RemoteJWKSet");
  }
  #url;
  #timeoutDuration;
  #cooldownDuration;
  #cacheMaxAge;
  #jwksTimestamp;
  #pendingFetch;
  #headers;
  #customFetch;
  #local;
  #cache;
  constructor(url, options) {
    if (!(url instanceof URL)) {
      throw new TypeError("url must be an instance of URL");
    }
    this.#url = new URL(url.href);
    this.#timeoutDuration = typeof options?.timeoutDuration === "number" ? options?.timeoutDuration : 5e3;
    this.#cooldownDuration = typeof options?.cooldownDuration === "number" ? options?.cooldownDuration : 3e4;
    this.#cacheMaxAge = typeof options?.cacheMaxAge === "number" ? options?.cacheMaxAge : 6e5;
    this.#headers = new Headers(options?.headers);
    if (USER_AGENT && !this.#headers.has("User-Agent")) {
      this.#headers.set("User-Agent", USER_AGENT);
    }
    if (!this.#headers.has("accept")) {
      this.#headers.set("accept", "application/json");
      this.#headers.append("accept", "application/jwk-set+json");
    }
    this.#customFetch = options?.[customFetch];
    if (options?.[jwksCache] !== void 0) {
      this.#cache = options?.[jwksCache];
      if (isFreshJwksCache(options?.[jwksCache], this.#cacheMaxAge)) {
        this.#jwksTimestamp = this.#cache.uat;
        this.#local = createLocalJWKSet(this.#cache.jwks);
      }
    }
  }
  pendingFetch() {
    return !!this.#pendingFetch;
  }
  coolingDown() {
    return typeof this.#jwksTimestamp === "number" ? Date.now() < this.#jwksTimestamp + this.#cooldownDuration : false;
  }
  fresh() {
    return typeof this.#jwksTimestamp === "number" ? Date.now() < this.#jwksTimestamp + this.#cacheMaxAge : false;
  }
  jwks() {
    return this.#local?.jwks();
  }
  async getKey(protectedHeader, token) {
    if (!this.#local || !this.fresh()) {
      await this.reload();
    }
    try {
      return await this.#local(protectedHeader, token);
    } catch (err) {
      if (err instanceof JWKSNoMatchingKey) {
        if (this.coolingDown() === false) {
          await this.reload();
          return this.#local(protectedHeader, token);
        }
      }
      throw err;
    }
  }
  async reload() {
    if (this.#pendingFetch && isCloudflareWorkers()) {
      this.#pendingFetch = void 0;
    }
    this.#pendingFetch ||= fetchJwks(this.#url.href, this.#headers, AbortSignal.timeout(this.#timeoutDuration), this.#customFetch).then((json2) => {
      this.#local = createLocalJWKSet(json2);
      if (this.#cache) {
        this.#cache.uat = Date.now();
        this.#cache.jwks = json2;
      }
      this.#jwksTimestamp = Date.now();
      this.#pendingFetch = void 0;
    }).catch((err) => {
      this.#pendingFetch = void 0;
      throw err;
    });
    await this.#pendingFetch;
  }
};
function createRemoteJWKSet(url, options) {
  const set = new RemoteJWKSet(url, options);
  const remoteJWKSet = /* @__PURE__ */ __name(async (protectedHeader, token) => set.getKey(protectedHeader, token), "remoteJWKSet");
  Object.defineProperties(remoteJWKSet, {
    coolingDown: {
      get: /* @__PURE__ */ __name(() => set.coolingDown(), "get"),
      enumerable: true,
      configurable: false
    },
    fresh: {
      get: /* @__PURE__ */ __name(() => set.fresh(), "get"),
      enumerable: true,
      configurable: false
    },
    reload: {
      value: /* @__PURE__ */ __name(() => set.reload(), "value"),
      enumerable: true,
      configurable: false,
      writable: false
    },
    reloading: {
      get: /* @__PURE__ */ __name(() => set.pendingFetch(), "get"),
      enumerable: true,
      configurable: false
    },
    jwks: {
      value: /* @__PURE__ */ __name(() => set.jwks(), "value"),
      enumerable: true,
      configurable: false,
      writable: false
    }
  });
  return remoteJWKSet;
}
__name(createRemoteJWKSet, "createRemoteJWKSet");

// src/firestore.js
var cachedToken = null;
var cachedTokenExpiry = 0;
var FirestoreClient = class {
  static {
    __name(this, "FirestoreClient");
  }
  constructor(env) {
    this.projectId = env.FIREBASE_PROJECT_ID;
    this.clientEmail = env.FIREBASE_CLIENT_EMAIL;
    this.privateKey = (env.FIREBASE_PRIVATE_KEY || "").replace(/\\n/g, "\n");
    this.baseUrl = `https://firestore.googleapis.com/v1/projects/${this.projectId}/databases/(default)/documents`;
  }
  async getDocument(path) {
    const token = await this.getAccessToken();
    const response = await fetch(`${this.baseUrl}/${path}`, {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
    if (response.status === 404) {
      return null;
    }
    if (!response.ok) {
      throw new Error(`Firestore GET failed for ${path}: ${await response.text()}`);
    }
    const document = await response.json();
    return fromFirestoreDocument(document);
  }
  async setDocument(path, data, options = {}) {
    const token = await this.getAccessToken();
    const query = new URLSearchParams();
    const useMerge = options.merge !== false;
    if (useMerge) {
      for (const key of Object.keys(data)) {
        query.append("updateMask.fieldPaths", key);
      }
    }
    const url = query.size > 0 ? `${this.baseUrl}/${path}?${query.toString()}` : `${this.baseUrl}/${path}`;
    const response = await fetch(url, {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        fields: toFirestoreFields(data)
      })
    });
    if (!response.ok) {
      throw new Error(`Firestore PATCH failed for ${path}: ${await response.text()}`);
    }
    return response.json();
  }
  async createDocumentIfMissing(path, data) {
    const token = await this.getAccessToken();
    const query = new URLSearchParams();
    query.append("currentDocument.exists", "false");
    const response = await fetch(`${this.baseUrl}/${path}?${query.toString()}`, {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        fields: toFirestoreFields(data)
      })
    });
    if (response.status === 409 || response.status === 412) {
      return false;
    }
    if (!response.ok) {
      throw new Error(`Firestore create-if-missing failed for ${path}: ${await response.text()}`);
    }
    return true;
  }
  async listDocuments(collectionPath) {
    const token = await this.getAccessToken();
    const response = await fetch(`${this.baseUrl}/${collectionPath}`, {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
    if (response.status === 404) {
      return [];
    }
    if (!response.ok) {
      throw new Error(`Firestore list failed for ${collectionPath}: ${await response.text()}`);
    }
    const payload = await response.json();
    return Array.isArray(payload.documents) ? payload.documents.map(fromFirestoreDocument) : [];
  }
  async getAccessToken() {
    const now = Date.now();
    if (cachedToken && now < cachedTokenExpiry) {
      return cachedToken;
    }
    if (!this.clientEmail || !this.privateKey || !this.projectId) {
      throw new Error("Missing FIREBASE_PROJECT_ID, FIREBASE_CLIENT_EMAIL or FIREBASE_PRIVATE_KEY");
    }
    const algorithm = "RS256";
    const privateKey = await importPKCS8(this.privateKey, algorithm);
    const issuedAt = Math.floor(now / 1e3);
    const expiresAt = issuedAt + 3600;
    const assertion = await new SignJWT({
      scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/cloud-platform"
    }).setProtectedHeader({ alg: algorithm, typ: "JWT" }).setIssuer(this.clientEmail).setSubject(this.clientEmail).setAudience("https://oauth2.googleapis.com/token").setIssuedAt(issuedAt).setExpirationTime(expiresAt).sign(privateKey);
    const response = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded"
      },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion
      })
    });
    const payload = await response.json();
    if (!response.ok || !payload.access_token) {
      throw new Error(`Unable to fetch Google access token: ${JSON.stringify(payload)}`);
    }
    cachedToken = payload.access_token;
    cachedTokenExpiry = now + ((Number(payload.expires_in) || 3600) - 60) * 1e3;
    return cachedToken;
  }
};
function fromFirestoreDocument(document) {
  return {
    id: document.name.split("/").pop(),
    path: document.name,
    createTime: document.createTime || null,
    updateTime: document.updateTime || null,
    ...fromFirestoreFields(document.fields || {})
  };
}
__name(fromFirestoreDocument, "fromFirestoreDocument");
function fromFirestoreFields(fields) {
  const result = {};
  for (const [key, value] of Object.entries(fields)) {
    result[key] = fromFirestoreValue(value);
  }
  return result;
}
__name(fromFirestoreFields, "fromFirestoreFields");
function fromFirestoreValue(value) {
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return Number(value.doubleValue);
  if ("booleanValue" in value) return Boolean(value.booleanValue);
  if ("nullValue" in value) return null;
  if ("timestampValue" in value) return value.timestampValue;
  if ("mapValue" in value) return fromFirestoreFields(value.mapValue.fields || {});
  if ("arrayValue" in value) {
    return Array.isArray(value.arrayValue.values) ? value.arrayValue.values.map(fromFirestoreValue) : [];
  }
  return null;
}
__name(fromFirestoreValue, "fromFirestoreValue");
function toFirestoreFields(data) {
  const result = {};
  for (const [key, value] of Object.entries(data)) {
    result[key] = toFirestoreValue(value);
  }
  return result;
}
__name(toFirestoreFields, "toFirestoreFields");
function toFirestoreValue(value) {
  if (value === null || value === void 0) return { nullValue: null };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") {
    if (Number.isInteger(value)) {
      return { integerValue: String(value) };
    }
    return { doubleValue: value };
  }
  if (Array.isArray(value)) {
    return {
      arrayValue: {
        values: value.map(toFirestoreValue)
      }
    };
  }
  if (typeof value === "object") {
    return {
      mapValue: {
        fields: toFirestoreFields(value)
      }
    };
  }
  return { stringValue: String(value) };
}
__name(toFirestoreValue, "toFirestoreValue");

// src/index.js
var FIREBASE_JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);
var corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Worker-Api-Key, X-User-Id"
};
var index_default = {
  async fetch(request, env, ctx) {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }
    const url = new URL(request.url);
    const firestore = new FirestoreClient(env);
    try {
      if (request.method === "GET" && url.pathname === "/health") {
        return json({
          success: true,
          worker: "refer-earn-worker",
          status: "running"
        });
      }
      const redirectMatch = url.pathname.match(/^\/r\/([A-Z0-9_-]+)$/i);
      if (request.method === "GET" && redirectMatch) {
        return handleReferralRedirect({
          referralCode: redirectMatch[1],
          request,
          env,
          ctx,
          firestore
        });
      }
      if (request.method === "POST" && url.pathname === "/api/referrals/generate") {
        const auth = await authenticateRequest(request, env);
        return handleGenerateCode({ request, env, firestore, auth });
      }
      if (request.method === "POST" && url.pathname === "/api/referrals/install-public") {
        return handleTrackAnonymousInstall({ request, firestore });
      }
      if (request.method === "POST" && url.pathname === "/api/referrals/install") {
        const auth = await authenticateRequest(request, env);
        return handleTrackInstall({ request, firestore, auth });
      }
      if (request.method === "POST" && url.pathname === "/api/referrals/claim") {
        const auth = await authenticateRequest(request, env);
        return handleClaimReferral({ request, firestore, auth });
      }
      if (request.method === "POST" && url.pathname === "/api/referrals/reward") {
        const auth = await authenticateRequest(request, env);
        return handleRewardReferral({ request, env, firestore, auth });
      }
      if (request.method === "GET" && url.pathname === "/api/referrals/stats") {
        const auth = await authenticateRequest(request, env);
        return handleReferralStats({ request, env, firestore, auth });
      }
      if (request.method === "GET" && url.pathname === "/api/referrals/clicks") {
        const auth = await authenticateRequest(request, env);
        return handleReferralClicks({ firestore, auth });
      }
      if (request.method === "GET" && url.pathname === "/api/referrals/installs") {
        const auth = await authenticateRequest(request, env);
        return handleReferralInstalls({ firestore, auth });
      }
      if (request.method === "GET" && url.pathname === "/api/referrals/referred-users") {
        const auth = await authenticateRequest(request, env);
        return handleReferredUsers({ firestore, auth });
      }
      return json({ success: false, error: "Not found" }, 404);
    } catch (error) {
      console.error("Affiliate worker error:", error);
      return json({
        success: false,
        error: error instanceof HttpError ? error.message : "Internal server error",
        ...error instanceof HttpError && error.details ? { details: error.details } : {}
      }, error instanceof HttpError ? error.status : 500);
    }
  }
};
async function handleGenerateCode({ request, env, firestore, auth }) {
  const body = await parseBody(request);
  const userPath = buildUserPath(auth.uid);
  const userDoc = await firestore.getDocument(userPath) || {};
  const fullName = pickFirstNonEmpty(
    body.fullName,
    userDoc.fullName,
    auth.name,
    userDoc.email,
    auth.email,
    auth.uid
  );
  const existingReferralCode = getExistingReferralCode(userDoc);
  if (existingReferralCode) {
    const now = (/* @__PURE__ */ new Date()).toISOString();
    await ensureReferralCodeMapping({
      firestore,
      referralCode: existingReferralCode,
      referrerUserId: auth.uid,
      referrerName: userDoc.fullName || auth.name || fullName,
      referrerEmail: userDoc.email || auth.email || ""
    });
    const canonicalLink = buildReferralLink(env, request, existingReferralCode);
    if (userDoc.myReferralCode !== existingReferralCode || userDoc.referralLink !== canonicalLink) {
      await firestore.setDocument(userPath, {
        userId: userDoc.userId || auth.uid,
        fullName: userDoc.fullName || auth.name || fullName,
        email: userDoc.email || auth.email || "",
        myReferralCode: existingReferralCode,
        referralLink: canonicalLink,
        updatedAt: now
      }, { merge: true });
    }
    return json({
      success: true,
      referralCode: existingReferralCode,
      referralLink: canonicalLink,
      message: "Affiliate code already exists"
    });
  }
  const referralCode = await generateUniqueReferralCode(firestore, fullName, auth.uid);
  const referralLink = buildReferralLink(env, request, referralCode);
  const now = (/* @__PURE__ */ new Date()).toISOString();
  await firestore.setDocument(`referralCodes/${referralCode}`, {
    referralCode,
    referrerUserId: auth.uid,
    referrerName: userDoc.fullName || auth.name || fullName,
    referrerEmail: userDoc.email || auth.email || "",
    createdAt: now,
    active: true
  });
  await firestore.setDocument(userPath, {
    userId: auth.uid,
    fullName: userDoc.fullName || auth.name || fullName,
    email: userDoc.email || auth.email || "",
    myReferralCode: referralCode,
    referralLink,
    referralCount: toInt(userDoc.referralCount),
    referralLinkClicks: toInt(userDoc.referralLinkClicks),
    trackedInstalls: toInt(userDoc.trackedInstalls),
    trackedRegistrations: toInt(userDoc.trackedRegistrations),
    successfulReferrals: toInt(userDoc.successfulReferrals),
    totalReferralEarnings: toInt(userDoc.totalReferralEarnings),
    pendingEarnings: toInt(userDoc.pendingEarnings),
    withdrawnEarnings: toInt(userDoc.withdrawnEarnings),
    updatedAt: now
  }, { merge: true });
  return json({
    success: true,
    referralCode,
    referralLink,
    message: "Affiliate code generated successfully"
  });
}
__name(handleGenerateCode, "handleGenerateCode");
async function handleTrackAnonymousInstall({ request, firestore }) {
  const body = await parseBody(request);
  const referralCode = sanitizeReferralCode(body.referralCode);
  const installId = sanitizeInstallId(body.installId);
  const installSource = normalizeInstallSource(body.source);
  if (!referralCode) {
    throw new HttpError(400, "referralCode is required");
  }
  if (!installId) {
    throw new HttpError(400, "installId is required");
  }
  const codeDoc = await firestore.getDocument(`referralCodes/${referralCode}`);
  if (!codeDoc || codeDoc.active === false) {
    throw new HttpError(404, "Affiliate code not found");
  }
  const installPath = buildInstallPath(installId);
  const now = (/* @__PURE__ */ new Date()).toISOString();
  const referrerPath = buildUserPath(codeDoc.referrerUserId);
  const referrerDoc = await firestore.getDocument(referrerPath) || {};
  const created = await firestore.createDocumentIfMissing(installPath, {
    installId,
    referralCode,
    referrerUserId: codeDoc.referrerUserId,
    installSource,
    installTrackedAt: now,
    createdAt: now,
    updatedAt: now
  });
  if (!created) {
    const existingInstall = await firestore.getDocument(installPath);
    return json({
      success: true,
      referralCode: existingInstall?.referralCode || referralCode,
      installId,
      message: "Affiliate install already tracked"
    });
  }
  await firestore.setDocument(referrerPath, {
    trackedInstalls: toInt(referrerDoc.trackedInstalls) + 1,
    updatedAt: now
  }, { merge: true });
  return json({
    success: true,
    referralCode,
    installId,
    message: "Anonymous affiliate install tracked successfully"
  });
}
__name(handleTrackAnonymousInstall, "handleTrackAnonymousInstall");
async function handleTrackInstall({ request, firestore, auth }) {
  const body = await parseBody(request);
  const installId = sanitizeInstallId(body.installId);
  const anonymousInstall = installId ? await firestore.getDocument(buildInstallPath(installId)) : null;
  const referralCode = sanitizeReferralCode(body.referralCode || anonymousInstall?.referralCode);
  const installSource = normalizeInstallSource(body.source || anonymousInstall?.installSource);
  if (!referralCode) {
    throw new HttpError(400, "referralCode is required");
  }
  const codeDoc = await firestore.getDocument(`referralCodes/${referralCode}`);
  if (!codeDoc || codeDoc.active === false) {
    throw new HttpError(404, "Affiliate code not found");
  }
  if (codeDoc.referrerUserId === auth.uid) {
    throw new HttpError(400, "You cannot use your own referral code");
  }
  if (anonymousInstall?.referrerUserId && anonymousInstall.referrerUserId !== codeDoc.referrerUserId) {
    throw new HttpError(409, "Install is already linked to another referrer");
  }
  const now = (/* @__PURE__ */ new Date()).toISOString();
  const userPath = buildUserPath(auth.uid);
  const currentUser = await firestore.getDocument(userPath) || {};
  const referrerPath = buildUserPath(codeDoc.referrerUserId);
  const referrerDoc = await firestore.getDocument(referrerPath) || {};
  const referredUserPath = `${referrerPath}/referredUsers/${auth.uid}`;
  const existingReferredUser = await firestore.getDocument(referredUserPath);
  const referredAt = existingReferredUser?.referredAt || now;
  let effectiveInstallTrackedAt = existingReferredUser?.installTrackedAt || anonymousInstall?.installTrackedAt || now;
  const effectiveInstallId = installId || anonymousInstall?.installId || null;
  let alreadyTrackedInstall = Boolean(existingReferredUser?.installTrackedAt) || Boolean(anonymousInstall?.installTrackedAt);
  const nextStatus = resolveLeadStatus(existingReferredUser?.userStatus, "installed");
  if (installId && !alreadyTrackedInstall) {
    const createdInstall = await firestore.createDocumentIfMissing(buildInstallPath(installId), {
      installId,
      referralCode,
      referrerUserId: codeDoc.referrerUserId,
      installSource,
      installTrackedAt: now,
      linkedUserId: auth.uid,
      linkedAt: now,
      createdAt: now,
      updatedAt: now
    });
    if (createdInstall) {
      effectiveInstallTrackedAt = now;
    } else {
      alreadyTrackedInstall = true;
      const latestInstall = await firestore.getDocument(buildInstallPath(installId));
      effectiveInstallTrackedAt = latestInstall?.installTrackedAt || effectiveInstallTrackedAt;
    }
  }
  await firestore.setDocument(userPath, {
    userId: auth.uid,
    installReferralCode: referralCode,
    referrerUserId: codeDoc.referrerUserId,
    referralInstallTrackedAt: effectiveInstallTrackedAt,
    installSource,
    affiliateInstallId: effectiveInstallId,
    updatedAt: now
  }, { merge: true });
  await firestore.setDocument(referredUserPath, {
    oderId: auth.uid,
    referredUserId: auth.uid,
    fullName: currentUser.fullName || auth.name || "New Install",
    email: currentUser.email || auth.email || "N/A",
    phoneNumber: currentUser.phoneNumber || "N/A",
    userStatus: nextStatus,
    referredAt,
    installTrackedAt: effectiveInstallTrackedAt,
    installSource,
    affiliateInstallId: effectiveInstallId,
    hasPurchased: Boolean(existingReferredUser?.hasPurchased),
    purchasedPlanType: existingReferredUser?.purchasedPlanType || null,
    purchaseAmount: toInt(existingReferredUser?.purchaseAmount),
    commissionEarned: toInt(existingReferredUser?.commissionEarned),
    registeredAt: existingReferredUser?.registeredAt || null,
    purchasedAt: existingReferredUser?.purchasedAt || null,
    lastUpdatedAt: now
  }, { merge: true });
  const referrerUpdates = {
    updatedAt: now
  };
  if (!existingReferredUser) {
    referrerUpdates.referralCount = toInt(referrerDoc.referralCount) + 1;
  }
  if (!alreadyTrackedInstall) {
    referrerUpdates.trackedInstalls = toInt(referrerDoc.trackedInstalls) + 1;
  }
  await firestore.setDocument(referrerPath, referrerUpdates, { merge: true });
  if (installId && (!anonymousInstall || !anonymousInstall.linkedUserId || anonymousInstall.linkedUserId === auth.uid)) {
    await firestore.setDocument(buildInstallPath(installId), {
      linkedUserId: auth.uid,
      linkedAt: anonymousInstall?.linkedAt || now,
      updatedAt: now
    }, { merge: true });
  }
  return json({
    success: true,
    message: "Affiliate install tracked successfully",
    referrerName: codeDoc.referrerName || referrerDoc.fullName || null
  });
}
__name(handleTrackInstall, "handleTrackInstall");
async function handleClaimReferral({ request, firestore, auth }) {
  const body = await parseBody(request);
  const installId = sanitizeInstallId(body.installId);
  const anonymousInstall = installId ? await firestore.getDocument(buildInstallPath(installId)) : null;
  const referralCode = sanitizeReferralCode(body.referralCode || anonymousInstall?.referralCode);
  if (!referralCode) {
    throw new HttpError(400, "referralCode is required");
  }
  const codeDoc = await firestore.getDocument(`referralCodes/${referralCode}`);
  if (!codeDoc || codeDoc.active === false) {
    throw new HttpError(404, "Affiliate code not found");
  }
  if (codeDoc.referrerUserId === auth.uid) {
    throw new HttpError(400, "You cannot use your own referral code");
  }
  if (anonymousInstall?.referrerUserId && anonymousInstall.referrerUserId !== codeDoc.referrerUserId) {
    throw new HttpError(409, "Install is already linked to another referrer");
  }
  const userPath = buildUserPath(auth.uid);
  const currentUser = await firestore.getDocument(userPath) || {};
  if (currentUser.referrerUserId && currentUser.referrerUserId !== codeDoc.referrerUserId) {
    throw new HttpError(409, "Referral code already linked to another referrer");
  }
  const referrerPath = buildUserPath(codeDoc.referrerUserId);
  const referrerDoc = await firestore.getDocument(referrerPath) || {};
  const referredUserPath = `${referrerPath}/referredUsers/${auth.uid}`;
  const existingReferredUser = await firestore.getDocument(referredUserPath);
  const isNewReferral = !existingReferredUser;
  const now = (/* @__PURE__ */ new Date()).toISOString();
  const referredAt = existingReferredUser?.referredAt || currentUser.referredAt || now;
  const wasRegistered = Boolean(existingReferredUser?.registeredAt);
  const userStatus = resolveLeadStatus(existingReferredUser?.userStatus || currentUser.userStatus, "registered");
  const effectiveInstallTrackedAt = existingReferredUser?.installTrackedAt || anonymousInstall?.installTrackedAt || currentUser.referralInstallTrackedAt || null;
  const effectiveInstallSource = existingReferredUser?.installSource || anonymousInstall?.installSource || currentUser.installSource || null;
  const effectiveInstallId = installId || anonymousInstall?.installId || currentUser.affiliateInstallId || null;
  await firestore.setDocument(userPath, {
    userId: auth.uid,
    fullName: currentUser.fullName || auth.name || "",
    email: currentUser.email || auth.email || "",
    referredBy: referralCode,
    referrerUserId: codeDoc.referrerUserId,
    referredAt,
    registeredAt: existingReferredUser?.registeredAt || now,
    installReferralCode: currentUser.installReferralCode || referralCode,
    referralInstallTrackedAt: effectiveInstallTrackedAt,
    installSource: effectiveInstallSource,
    affiliateInstallId: effectiveInstallId,
    updatedAt: now
  }, { merge: true });
  await firestore.setDocument(referredUserPath, {
    oderId: auth.uid,
    referredUserId: auth.uid,
    fullName: currentUser.fullName || auth.name || "Unknown",
    email: currentUser.email || auth.email || "N/A",
    phoneNumber: currentUser.phoneNumber || "N/A",
    userStatus,
    referredAt,
    installTrackedAt: effectiveInstallTrackedAt,
    installSource: effectiveInstallSource,
    affiliateInstallId: effectiveInstallId,
    registeredAt: existingReferredUser?.registeredAt || now,
    hasPurchased: Boolean(existingReferredUser?.hasPurchased),
    purchasedPlanType: existingReferredUser?.purchasedPlanType || null,
    purchaseAmount: toInt(existingReferredUser?.purchaseAmount),
    commissionEarned: toInt(existingReferredUser?.commissionEarned),
    purchasedAt: existingReferredUser?.purchasedAt || null,
    lastUpdatedAt: now
  }, { merge: true });
  const referrerUpdates = {
    updatedAt: now
  };
  if (isNewReferral) {
    referrerUpdates.referralCount = toInt(referrerDoc.referralCount) + 1;
  }
  if (!wasRegistered) {
    referrerUpdates.trackedRegistrations = toInt(referrerDoc.trackedRegistrations) + 1;
  }
  await firestore.setDocument(referrerPath, referrerUpdates, { merge: true });
  if (installId && (!anonymousInstall || !anonymousInstall.linkedUserId || anonymousInstall.linkedUserId === auth.uid)) {
    await firestore.setDocument(buildInstallPath(installId), {
      linkedUserId: auth.uid,
      linkedAt: anonymousInstall?.linkedAt || now,
      updatedAt: now
    }, { merge: true });
  }
  return json({
    success: true,
    message: "Affiliate signup tracked successfully",
    referrerName: codeDoc.referrerName || referrerDoc.fullName || null
  });
}
__name(handleClaimReferral, "handleClaimReferral");
async function handleRewardReferral({ request, env, firestore, auth }) {
  const body = await parseBody(request);
  const planType = sanitizePlanType(body.planType);
  const purchaseAmount = resolvePurchaseAmount(planType, body.purchaseAmount);
  if (!planType || purchaseAmount <= 0) {
    throw new HttpError(400, "planType and purchaseAmount are required");
  }
  const buyerPath = buildUserPath(auth.uid);
  const buyerDoc = await firestore.getDocument(buyerPath);
  if (!buyerDoc) {
    throw new HttpError(404, "Buyer userDetails document not found");
  }
  const referrerUserId = buyerDoc.referrerUserId || null;
  if (!referrerUserId) {
    return json({
      success: false,
      message: "No referrer linked for this user"
    });
  }
  const referredUserPath = `${buildUserPath(referrerUserId)}/referredUsers/${auth.uid}`;
  const referredUserDoc = await firestore.getDocument(referredUserPath);
  if (!referredUserDoc) {
    return json({
      success: false,
      message: "Affiliate record not found for this user"
    });
  }
  if (referredUserDoc.hasPurchased) {
    return json({
      success: true,
      message: "Affiliate reward already processed",
      commission: toInt(referredUserDoc.commissionEarned)
    });
  }
  const commissionRate = resolveCommissionRate(env.REFERRAL_COMMISSION_RATE);
  const commission = Math.round(purchaseAmount * commissionRate / 100);
  const now = (/* @__PURE__ */ new Date()).toISOString();
  const referrerPath = buildUserPath(referrerUserId);
  const referrerDoc = await firestore.getDocument(referrerPath) || {};
  await firestore.setDocument(referredUserPath, {
    oderId: auth.uid,
    referredUserId: auth.uid,
    fullName: buyerDoc.fullName || auth.name || "Unknown",
    email: buyerDoc.email || auth.email || "N/A",
    phoneNumber: buyerDoc.phoneNumber || "N/A",
    userStatus: "purchased",
    hasPurchased: true,
    purchasedPlanType: planType,
    purchaseAmount,
    commissionEarned: commission,
    installTrackedAt: referredUserDoc.installTrackedAt || null,
    installSource: referredUserDoc.installSource || null,
    registeredAt: referredUserDoc.registeredAt || null,
    purchasedAt: now,
    lastUpdatedAt: now
  }, { merge: true });
  await firestore.setDocument(buyerPath, {
    userStatus: "purchased",
    purchasedPlanType: planType,
    lastReferralRewardProcessedAt: now,
    updatedAt: now
  }, { merge: true });
  await firestore.setDocument(referrerPath, {
    successfulReferrals: toInt(referrerDoc.successfulReferrals) + 1,
    totalReferralEarnings: toInt(referrerDoc.totalReferralEarnings) + commission,
    pendingEarnings: toInt(referrerDoc.pendingEarnings) + commission,
    updatedAt: now
  }, { merge: true });
  return json({
    success: true,
    message: "Affiliate reward processed successfully",
    commission
  });
}
__name(handleRewardReferral, "handleRewardReferral");
async function handleReferralStats({ request, env, firestore, auth }) {
  const userPath = buildUserPath(auth.uid);
  const userDoc = await firestore.getDocument(userPath) || {};
  const now = (/* @__PURE__ */ new Date()).toISOString();
  const fullName = pickFirstNonEmpty(
    userDoc.fullName,
    auth.name,
    userDoc.email,
    auth.email,
    auth.uid
  );
  let referralCode = getExistingReferralCode(userDoc);
  let referralLink = null;
  if (referralCode) {
    await ensureReferralCodeMapping({
      firestore,
      referralCode,
      referrerUserId: auth.uid,
      referrerName: userDoc.fullName || auth.name || fullName,
      referrerEmail: userDoc.email || auth.email || ""
    });
    referralLink = buildReferralLink(env, request, referralCode);
    if (userDoc.myReferralCode !== referralCode || userDoc.referralLink !== referralLink) {
      await firestore.setDocument(userPath, {
        userId: userDoc.userId || auth.uid,
        fullName: userDoc.fullName || auth.name || fullName,
        email: userDoc.email || auth.email || "",
        myReferralCode: referralCode,
        referralLink,
        updatedAt: now
      }, { merge: true });
    }
  } else {
    referralCode = await generateUniqueReferralCode(firestore, fullName, auth.uid);
    referralLink = buildReferralLink(env, request, referralCode);
    await firestore.setDocument(`referralCodes/${referralCode}`, {
      referralCode,
      referrerUserId: auth.uid,
      referrerName: userDoc.fullName || auth.name || fullName,
      referrerEmail: userDoc.email || auth.email || "",
      createdAt: now,
      active: true
    });
    await firestore.setDocument(userPath, {
      userId: auth.uid,
      fullName: userDoc.fullName || auth.name || fullName,
      email: userDoc.email || auth.email || "",
      myReferralCode: referralCode,
      referralLink,
      referralCount: toInt(userDoc.referralCount),
      referralLinkClicks: toInt(userDoc.referralLinkClicks),
      trackedInstalls: toInt(userDoc.trackedInstalls),
      trackedRegistrations: toInt(userDoc.trackedRegistrations),
      successfulReferrals: toInt(userDoc.successfulReferrals),
      totalReferralEarnings: toInt(userDoc.totalReferralEarnings),
      pendingEarnings: toInt(userDoc.pendingEarnings),
      withdrawnEarnings: toInt(userDoc.withdrawnEarnings),
      updatedAt: now
    }, { merge: true });
  }
  const clickDocs = await firestore.listDocuments(`${userPath}/referralClicks`);
  const referralLinkClicks = Math.max(toInt(userDoc.referralLinkClicks), clickDocs.length);
  return json({
    success: true,
    stats: {
      myReferralCode: referralCode || null,
      referralLink: referralLink || userDoc.referralLink || null,
      referralCount: toInt(userDoc.referralCount),
      referralLinkClicks,
      trackedInstalls: toInt(userDoc.trackedInstalls),
      trackedRegistrations: toInt(userDoc.trackedRegistrations),
      successfulReferrals: toInt(userDoc.successfulReferrals),
      totalReferralEarnings: toInt(userDoc.totalReferralEarnings),
      pendingEarnings: toInt(userDoc.pendingEarnings),
      withdrawnEarnings: toInt(userDoc.withdrawnEarnings),
      referredBy: userDoc.referredBy || null
    }
  });
}
__name(handleReferralStats, "handleReferralStats");
async function handleReferredUsers({ firestore, auth }) {
  const userDoc = await firestore.getDocument(buildUserPath(auth.uid)) || {};
  const referredUsers = await firestore.listDocuments(`${buildUserPath(auth.uid)}/referredUsers`);
  const normalizedUsers = referredUsers.map((user) => ({
    oderId: user.oderId || user.referredUserId || user.id || "",
    fullName: user.fullName || "Unknown",
    email: user.email || "N/A",
    phoneNumber: user.phoneNumber || "N/A",
    userStatus: user.userStatus || "registered",
    referredAt: user.referredAt || null,
    installTrackedAt: user.installTrackedAt || null,
    registeredAt: user.registeredAt || null,
    purchasedAt: user.purchasedAt || null,
    installSource: user.installSource || null,
    hasPurchased: Boolean(user.hasPurchased),
    purchasedPlanType: user.purchasedPlanType || null,
    purchaseAmount: toInt(user.purchaseAmount),
    commissionEarned: toInt(user.commissionEarned)
  })).sort((left, right) => {
    const leftTime = left.purchasedAt || left.registeredAt || left.installTrackedAt || left.referredAt;
    const rightTime = right.purchasedAt || right.registeredAt || right.installTrackedAt || right.referredAt;
    const leftDate = leftTime ? Date.parse(leftTime) : 0;
    const rightDate = rightTime ? Date.parse(rightTime) : 0;
    return rightDate - leftDate;
  });
  return json({
    success: true,
    referralCode: userDoc.myReferralCode || null,
    totalReferred: normalizedUsers.length,
    referredUsers: normalizedUsers
  });
}
__name(handleReferredUsers, "handleReferredUsers");
async function handleReferralClicks({ firestore, auth }) {
  const userPath = buildUserPath(auth.uid);
  const userDoc = await firestore.getDocument(userPath) || {};
  const clickDocs = await firestore.listDocuments(`${userPath}/referralClicks`);
  const clickHistory = clickDocs.map((click) => ({
    clickId: click.id || "",
    referralCode: click.referralCode || userDoc.myReferralCode || null,
    clickedAt: click.clickedAt || click.createTime || null,
    userAgent: click.userAgent || ""
  })).sort((left, right) => {
    const leftDate = left.clickedAt ? Date.parse(left.clickedAt) : 0;
    const rightDate = right.clickedAt ? Date.parse(right.clickedAt) : 0;
    return rightDate - leftDate;
  });
  return json({
    success: true,
    totalClicks: Math.max(toInt(userDoc.referralLinkClicks), clickHistory.length),
    clickHistory
  });
}
__name(handleReferralClicks, "handleReferralClicks");
async function handleReferralInstalls({ firestore, auth }) {
  const installDocs = await firestore.listDocuments("referralInstalls");
  const installHistory = installDocs.filter((install) => install.referrerUserId === auth.uid).map((install) => ({
    installId: install.installId || install.id || "",
    referralCode: install.referralCode || null,
    installTrackedAt: install.installTrackedAt || install.createTime || null,
    installSource: install.installSource || null,
    linkedUserId: install.linkedUserId || null
  })).sort((left, right) => {
    const leftDate = left.installTrackedAt ? Date.parse(left.installTrackedAt) : 0;
    const rightDate = right.installTrackedAt ? Date.parse(right.installTrackedAt) : 0;
    return rightDate - leftDate;
  });
  return json({
    success: true,
    totalInstalls: installHistory.length,
    installHistory
  });
}
__name(handleReferralInstalls, "handleReferralInstalls");
async function handleReferralRedirect({ referralCode, request, env, ctx, firestore }) {
  const normalizedCode = sanitizeReferralCode(referralCode);
  if (!normalizedCode) {
    throw new HttpError(400, "Invalid referral code");
  }
  const codeDoc = await firestore.getDocument(`referralCodes/${normalizedCode}`);
  if (codeDoc?.referrerUserId) {
    const referrerPath = buildUserPath(codeDoc.referrerUserId);
    const referrerDoc = await firestore.getDocument(referrerPath) || {};
    const now = (/* @__PURE__ */ new Date()).toISOString();
    try {
      await Promise.all([
        firestore.setDocument(referrerPath, {
          referralLinkClicks: toInt(referrerDoc.referralLinkClicks) + 1,
          updatedAt: now
        }, { merge: true }),
        firestore.setDocument(`${referrerPath}/referralClicks/${crypto.randomUUID()}`, {
          referralCode: normalizedCode,
          clickedAt: now,
          userAgent: request.headers.get("user-agent") || ""
        }, { merge: true })
      ]);
    } catch (error) {
      console.error("Failed to log referral click", error);
    }
  }
  return Response.redirect(buildPlayStoreUrl(env, normalizedCode), 302);
}
__name(handleReferralRedirect, "handleReferralRedirect");
async function ensureReferralCodeMapping({
  firestore,
  referralCode,
  referrerUserId,
  referrerName,
  referrerEmail
}) {
  const normalizedCode = sanitizeReferralCode(referralCode);
  if (!normalizedCode || !referrerUserId) {
    return null;
  }
  const path = `referralCodes/${normalizedCode}`;
  const existingDoc = await firestore.getDocument(path);
  const payload = {};
  if (!existingDoc?.referralCode) {
    payload.referralCode = normalizedCode;
  }
  if (existingDoc?.referrerUserId !== referrerUserId) {
    payload.referrerUserId = referrerUserId;
  }
  if (!existingDoc?.referrerName && referrerName) {
    payload.referrerName = referrerName;
  }
  if (!existingDoc?.referrerEmail && referrerEmail) {
    payload.referrerEmail = referrerEmail;
  }
  if (!existingDoc || typeof existingDoc.active === "undefined") {
    payload.active = true;
  }
  if (!existingDoc?.createdAt) {
    payload.createdAt = (/* @__PURE__ */ new Date()).toISOString();
  }
  if (Object.keys(payload).length > 0) {
    await firestore.setDocument(path, payload, { merge: true });
  }
  return normalizedCode;
}
__name(ensureReferralCodeMapping, "ensureReferralCodeMapping");
async function authenticateRequest(request, env) {
  const adminKey = request.headers.get("X-Worker-Api-Key");
  const adminUserId = request.headers.get("X-User-Id");
  if (env.ADMIN_API_KEY && adminKey && adminKey === env.ADMIN_API_KEY) {
    if (!adminUserId) {
      throw new HttpError(400, "X-User-Id header is required with X-Worker-Api-Key");
    }
    return {
      uid: adminUserId,
      email: null,
      name: null,
      authSource: "admin-key"
    };
  }
  const authorization = request.headers.get("Authorization") || "";
  if (!authorization.startsWith("Bearer ")) {
    throw new HttpError(401, "Missing Bearer token");
  }
  const idToken = authorization.slice("Bearer ".length).trim();
  if (!idToken) {
    throw new HttpError(401, "Missing Firebase ID token");
  }
  let verified;
  try {
    verified = await jwtVerify(idToken, FIREBASE_JWKS, {
      issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
      audience: env.FIREBASE_PROJECT_ID
    });
  } catch (error) {
    throw new HttpError(401, "Invalid Firebase ID token", {
      reason: error instanceof Error ? error.message : "jwt verification failed"
    });
  }
  return {
    uid: verified.payload.sub,
    email: verified.payload.email || null,
    name: verified.payload.name || null,
    authSource: "firebase-id-token"
  };
}
__name(authenticateRequest, "authenticateRequest");
async function generateUniqueReferralCode(firestore, fullName, userId) {
  const prefix = createCodePrefix(fullName);
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const suffix = attempt === 0 ? userId.replace(/[^a-z0-9]/gi, "").slice(-4).toUpperCase() : randomAlphaNumeric(4 + attempt);
    const candidate = sanitizeReferralCode(`${prefix}${suffix}`);
    if (!candidate) {
      continue;
    }
    const existing = await firestore.getDocument(`referralCodes/${candidate}`);
    if (!existing) {
      return candidate;
    }
  }
  throw new HttpError(500, "Unable to generate unique referral code");
}
__name(generateUniqueReferralCode, "generateUniqueReferralCode");
function buildReferralLink(env, request, referralCode) {
  const base = (env.REFERRAL_PUBLIC_BASE_URL || new URL(request.url).origin).replace(/\/+$/, "");
  return `${base}/r/${referralCode}`;
}
__name(buildReferralLink, "buildReferralLink");
function buildPlayStoreUrl(env, referralCode) {
  const packageName = env.ANDROID_PACKAGE_NAME || "com.message.bulksend";
  const encodedReferrer = encodeURIComponent(`ref_${referralCode}`);
  return `https://play.google.com/store/apps/details?id=${packageName}&referrer=${encodedReferrer}`;
}
__name(buildPlayStoreUrl, "buildPlayStoreUrl");
function buildUserPath(userId) {
  return `userDetails/${userId}`;
}
__name(buildUserPath, "buildUserPath");
function getExistingReferralCode(userDoc) {
  return sanitizeReferralCode(
    pickFirstNonEmpty(
      userDoc?.myReferralCode,
      userDoc?.referralCode,
      userDoc?.affiliateCode,
      userDoc?.affiliateReferralCode
    )
  );
}
__name(getExistingReferralCode, "getExistingReferralCode");
function sanitizeReferralCode(value) {
  if (value === void 0 || value === null) {
    return "";
  }
  return String(value).trim().toUpperCase().replace(/[^A-Z0-9_-]/g, "").slice(0, 20);
}
__name(sanitizeReferralCode, "sanitizeReferralCode");
function sanitizePlanType(value) {
  if (!value) {
    return "";
  }
  return String(value).trim().toLowerCase();
}
__name(sanitizePlanType, "sanitizePlanType");
function sanitizeInstallId(value) {
  if (!value) {
    return "";
  }
  return String(value).trim().replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 80);
}
__name(sanitizeInstallId, "sanitizeInstallId");
function normalizeInstallSource(value) {
  if (!value) {
    return "play_store_install";
  }
  return String(value).trim().toLowerCase().replace(/[^a-z0-9_-]/g, "_") || "play_store_install";
}
__name(normalizeInstallSource, "normalizeInstallSource");
function resolveLeadStatus(currentStatus, fallbackStatus) {
  if (currentStatus === "purchased") return "purchased";
  if (currentStatus === "registered" && fallbackStatus === "installed") return "registered";
  if (currentStatus === "installed" && fallbackStatus === "registered") return "registered";
  return currentStatus || fallbackStatus;
}
__name(resolveLeadStatus, "resolveLeadStatus");
function resolvePurchaseAmount(planType, explicitAmount) {
  const numericAmount = Number(explicitAmount);
  if (Number.isFinite(numericAmount) && numericAmount > 0) {
    return Math.round(numericAmount);
  }
  const fallbackAmount = {
    monthly: 299,
    yearly: 1499,
    lifetime: 2999,
    aiagent499: 499,
    ai_monthly: 199,
    ai_yearly: 899
  };
  return fallbackAmount[planType] || 0;
}
__name(resolvePurchaseAmount, "resolvePurchaseAmount");
function resolveCommissionRate(value) {
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) {
    return numeric;
  }
  return 30;
}
__name(resolveCommissionRate, "resolveCommissionRate");
function createCodePrefix(fullName) {
  const letters = String(fullName || "USER").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 4);
  return letters.padEnd(4, "X");
}
__name(createCodePrefix, "createCodePrefix");
function buildInstallPath(installId) {
  return `referralInstalls/${installId}`;
}
__name(buildInstallPath, "buildInstallPath");
function randomAlphaNumeric(length) {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let result = "";
  for (let index = 0; index < length; index += 1) {
    result += alphabet[Math.floor(Math.random() * alphabet.length)];
  }
  return result;
}
__name(randomAlphaNumeric, "randomAlphaNumeric");
function pickFirstNonEmpty(...values) {
  for (const value of values) {
    if (value !== void 0 && value !== null && String(value).trim()) {
      return String(value).trim();
    }
  }
  return "";
}
__name(pickFirstNonEmpty, "pickFirstNonEmpty");
function toInt(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? Math.trunc(numeric) : 0;
}
__name(toInt, "toInt");
async function parseBody(request) {
  let body;
  try {
    body = await request.json();
  } catch {
    throw new HttpError(400, "Invalid JSON body");
  }
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    throw new HttpError(400, "JSON body must be an object");
  }
  return body;
}
__name(parseBody, "parseBody");
function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...corsHeaders
    }
  });
}
__name(json, "json");
var HttpError = class extends Error {
  static {
    __name(this, "HttpError");
  }
  constructor(status, message2, details = null) {
    super(message2);
    this.name = "HttpError";
    this.status = status;
    this.details = details;
  }
};
export {
  index_default as default
};
//# sourceMappingURL=index.js.map
