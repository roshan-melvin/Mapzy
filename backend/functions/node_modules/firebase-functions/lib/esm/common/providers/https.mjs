import { debug, error, warn } from "../../logger/index.mjs";
import { getApp } from "../app.mjs";
import { isDebugFeatureEnabled } from "../debug.mjs";
import { getAuth } from "firebase-admin/auth";
import cors from "cors";
import { getAppCheck } from "firebase-admin/app-check";

//#region src/common/providers/https.ts
const JWT_REGEX = /^[a-zA-Z0-9\-_=]+?\.[a-zA-Z0-9\-_=]+?\.([a-zA-Z0-9\-_=]+)?$/;
/** @internal */
const CALLABLE_AUTH_HEADER = "x-callable-context-auth";
/** @internal */
const ORIGINAL_AUTH_HEADER = "x-original-auth";
/** @internal */
const DEFAULT_HEARTBEAT_SECONDS = 30;
/**
* Standard error codes and HTTP statuses for different ways a request can fail,
* as defined by:
* https://github.com/googleapis/googleapis/blob/master/google/rpc/code.proto
*
* This map is used primarily to convert from a client error code string to
* to the HTTP format error code string and status, and make sure it's in the
* supported set.
*/
const errorCodeMap = {
	ok: {
		canonicalName: "OK",
		status: 200
	},
	cancelled: {
		canonicalName: "CANCELLED",
		status: 499
	},
	unknown: {
		canonicalName: "UNKNOWN",
		status: 500
	},
	"invalid-argument": {
		canonicalName: "INVALID_ARGUMENT",
		status: 400
	},
	"deadline-exceeded": {
		canonicalName: "DEADLINE_EXCEEDED",
		status: 504
	},
	"not-found": {
		canonicalName: "NOT_FOUND",
		status: 404
	},
	"already-exists": {
		canonicalName: "ALREADY_EXISTS",
		status: 409
	},
	"permission-denied": {
		canonicalName: "PERMISSION_DENIED",
		status: 403
	},
	unauthenticated: {
		canonicalName: "UNAUTHENTICATED",
		status: 401
	},
	"resource-exhausted": {
		canonicalName: "RESOURCE_EXHAUSTED",
		status: 429
	},
	"failed-precondition": {
		canonicalName: "FAILED_PRECONDITION",
		status: 400
	},
	aborted: {
		canonicalName: "ABORTED",
		status: 409
	},
	"out-of-range": {
		canonicalName: "OUT_OF_RANGE",
		status: 400
	},
	unimplemented: {
		canonicalName: "UNIMPLEMENTED",
		status: 501
	},
	internal: {
		canonicalName: "INTERNAL",
		status: 500
	},
	unavailable: {
		canonicalName: "UNAVAILABLE",
		status: 503
	},
	"data-loss": {
		canonicalName: "DATA_LOSS",
		status: 500
	}
};
/**
* An explicit error that can be thrown from a handler to send an error to the
* client that called the function.
*/
var HttpsError = class extends Error {
	constructor(code, message, details) {
		super(message);
		if (code in errorCodeMap === false) {
			throw new Error(`Unknown error code: ${code}.`);
		}
		this.code = code;
		this.details = details;
		this.httpErrorCode = errorCodeMap[code];
	}
	/**
	* Returns a JSON-serializable representation of this object.
	*/
	toJSON() {
		const { details, httpErrorCode: { canonicalName: status }, message } = this;
		return {
			...details === undefined ? {} : { details },
			message,
			status
		};
	}
};
/** @hidden */
function isValidRequest(req) {
	if (!req.body) {
		warn("Request is missing body.");
		return false;
	}
	if (req.method !== "POST") {
		warn("Request has invalid method.", req.method);
		return false;
	}
	let contentType = (req.header("Content-Type") || "").toLowerCase();
	const semiColon = contentType.indexOf(";");
	if (semiColon >= 0) {
		contentType = contentType.slice(0, semiColon).trim();
	}
	if (contentType !== "application/json") {
		warn("Request has incorrect Content-Type.", contentType);
		return false;
	}
	if (typeof req.body.data === "undefined") {
		warn("Request body is missing data.", req.body);
		return false;
	}
	const extraKeys = Object.keys(req.body).filter((field) => field !== "data");
	if (extraKeys.length !== 0) {
		warn("Request body has extra fields: ", extraKeys.join(", "));
		return false;
	}
	return true;
}
/** @hidden */
const LONG_TYPE = "type.googleapis.com/google.protobuf.Int64Value";
/** @hidden */
const UNSIGNED_LONG_TYPE = "type.googleapis.com/google.protobuf.UInt64Value";
/**
* Encodes arbitrary data in our special format for JSON.
* This is exposed only for testing.
*/
/** @hidden */
function encode(data) {
	if (data === null || typeof data === "undefined") {
		return null;
	}
	if (data instanceof Number) {
		data = data.valueOf();
	}
	if (Number.isFinite(data)) {
		return data;
	}
	if (typeof data === "boolean") {
		return data;
	}
	if (typeof data === "string") {
		return data;
	}
	if (Array.isArray(data)) {
		return data.map(encode);
	}
	if (typeof data === "object" || typeof data === "function") {
		const obj = {};
		for (const [k, v] of Object.entries(data)) {
			obj[k] = encode(v);
		}
		return obj;
	}
	error("Data cannot be encoded in JSON.", data);
	throw new Error(`Data cannot be encoded in JSON: ${data}`);
}
/**
* Decodes our special format for JSON into native types.
* This is exposed only for testing.
*/
/** @hidden */
function decode(data) {
	if (data === null) {
		return data;
	}
	if (data["@type"]) {
		switch (data["@type"]) {
			case LONG_TYPE:
			case UNSIGNED_LONG_TYPE: {
				const value = parseFloat(data.value);
				if (isNaN(value)) {
					error("Data cannot be decoded from JSON.", data);
					throw new Error(`Data cannot be decoded from JSON: ${data}`);
				}
				return value;
			}
			default: {
				error("Data cannot be decoded from JSON.", data);
				throw new Error(`Data cannot be decoded from JSON: ${data}`);
			}
		}
	}
	if (Array.isArray(data)) {
		return data.map(decode);
	}
	if (typeof data === "object") {
		const obj = {};
		for (const [k, v] of Object.entries(data)) {
			obj[k] = decode(v);
		}
		return obj;
	}
	return data;
}
/** @internal */
function unsafeDecodeToken(token) {
	if (!JWT_REGEX.test(token)) {
		return {};
	}
	const components = token.split(".").map((s) => Buffer.from(s, "base64").toString());
	let payload = components[1];
	if (typeof payload === "string") {
		try {
			const obj = JSON.parse(payload);
			if (typeof obj === "object") {
				payload = obj;
			}
		} catch (_e) {}
	}
	return payload;
}
/**
* Decode, but not verify, a Auth ID token.
*
* Do not use in production. Token should always be verified using the Admin SDK.
*
* This is exposed only for testing.
*/
/** @internal */
function unsafeDecodeIdToken(token) {
	const decoded = unsafeDecodeToken(token);
	decoded.uid = decoded.sub;
	return decoded;
}
/**
* Decode, but not verify, an App Check token.
*
* Do not use in production. Token should always be verified using the Admin SDK.
*
* This is exposed only for testing.
*/
/** @internal */
function unsafeDecodeAppCheckToken(token) {
	const decoded = unsafeDecodeToken(token);
	decoded.app_id = decoded.sub;
	return decoded;
}
/**
* Check and verify tokens included in the requests. Once verified, tokens
* are injected into the callable context.
*
* @param {Request} req - Request sent to the Callable function.
* @param {CallableContext} ctx - Context to be sent to callable function handler.
* @returns {CallableTokenStatus} Status of the token verifications.
*/
/** @internal */
async function checkTokens(req, ctx, options) {
	const verifications = {
		app: "INVALID",
		auth: "INVALID"
	};
	[verifications.auth, verifications.app] = await Promise.all([checkAuthToken(req, ctx), checkAppCheckToken(req, ctx, options)]);
	const logPayload = {
		verifications,
		"logging.googleapis.com/labels": { "firebase-log-type": "callable-request-verification" }
	};
	const errs = [];
	if (verifications.app === "INVALID") {
		errs.push("AppCheck token was rejected.");
	}
	if (verifications.auth === "INVALID") {
		errs.push("Auth token was rejected.");
	}
	if (errs.length === 0) {
		debug("Callable request verification passed", logPayload);
	} else {
		warn(`Callable request verification failed: ${errs.join(" ")}`, logPayload);
	}
	return verifications;
}
/** @interanl */
async function checkAuthToken(req, ctx) {
	const authorization = req.header("Authorization");
	if (!authorization) {
		return "MISSING";
	}
	const match = authorization.match(/^Bearer (.*)$/i);
	if (!match) {
		return "INVALID";
	}
	const idToken = match[1];
	try {
		let authToken;
		if (isDebugFeatureEnabled("skipTokenVerification")) {
			authToken = unsafeDecodeIdToken(idToken);
		} else {
			authToken = await getAuth(getApp()).verifyIdToken(idToken);
		}
		ctx.auth = {
			uid: authToken.uid,
			token: authToken,
			rawToken: idToken
		};
		return "VALID";
	} catch (err) {
		warn("Failed to validate auth token.", err);
		return "INVALID";
	}
}
/** @internal */
async function checkAppCheckToken(req, ctx, options) {
	const appCheckToken = req.header("X-Firebase-AppCheck");
	if (!appCheckToken) {
		return "MISSING";
	}
	try {
		let appCheckData;
		if (isDebugFeatureEnabled("skipTokenVerification")) {
			const decodedToken = unsafeDecodeAppCheckToken(appCheckToken);
			appCheckData = {
				appId: decodedToken.app_id,
				token: decodedToken
			};
			if (options.consumeAppCheckToken) {
				appCheckData.alreadyConsumed = false;
			}
		} else {
			const appCheck = getAppCheck(getApp());
			if (options.consumeAppCheckToken) {
				if (appCheck.verifyToken?.length === 1) {
					const errorMsg = "Unsupported version of the Admin SDK." + " App Check token will not be consumed." + " Please upgrade the firebase-admin to the latest version.";
					error(errorMsg);
					throw new HttpsError("internal", "Internal Error");
				}
				appCheckData = await getAppCheck(getApp()).verifyToken(appCheckToken, { consume: true });
			} else {
				appCheckData = await getAppCheck(getApp()).verifyToken(appCheckToken);
			}
		}
		ctx.app = appCheckData;
		return "VALID";
	} catch (err) {
		warn("Failed to validate AppCheck token.", err);
		if (err instanceof HttpsError) {
			throw err;
		}
		return "INVALID";
	}
}
/** @internal */
function onCallHandler(options, handler, version) {
	const wrapped = wrapOnCallHandler(options, handler, version);
	return (req, res) => {
		return new Promise((resolve) => {
			res.on("finish", resolve);
			cors(options.cors)(req, res, () => {
				resolve(wrapped(req, res));
			});
		});
	};
}
function encodeSSE(data) {
	return `data: ${JSON.stringify(data)}\n\n`;
}
/** @internal */
function wrapOnCallHandler(options, handler, version) {
	return async (req, res) => {
		const abortController = new AbortController();
		let heartbeatInterval = null;
		const heartbeatSeconds = options.heartbeatSeconds === undefined ? DEFAULT_HEARTBEAT_SECONDS : options.heartbeatSeconds;
		const clearScheduledHeartbeat = () => {
			if (heartbeatInterval) {
				clearTimeout(heartbeatInterval);
				heartbeatInterval = null;
			}
		};
		const scheduleHeartbeat = () => {
			clearScheduledHeartbeat();
			if (!abortController.signal.aborted) {
				heartbeatInterval = setTimeout(() => {
					if (!abortController.signal.aborted) {
						res.write(": ping\n\n");
						scheduleHeartbeat();
					}
				}, heartbeatSeconds * 1e3);
			}
		};
		res.on("close", () => {
			clearScheduledHeartbeat();
			abortController.abort();
		});
		try {
			if (!isValidRequest(req)) {
				error("Invalid request, unable to process.");
				throw new HttpsError("invalid-argument", "Bad Request");
			}
			const context = { rawRequest: req };
			if (isDebugFeatureEnabled("skipTokenVerification") && version === "gcfv1") {
				const authContext = context.rawRequest.header(CALLABLE_AUTH_HEADER);
				if (authContext) {
					debug("Callable functions auth override", {
						key: CALLABLE_AUTH_HEADER,
						value: authContext
					});
					context.auth = JSON.parse(decodeURIComponent(authContext));
					delete context.rawRequest.headers[CALLABLE_AUTH_HEADER];
				}
				const originalAuth = context.rawRequest.header(ORIGINAL_AUTH_HEADER);
				if (originalAuth) {
					context.rawRequest.headers["authorization"] = originalAuth;
					delete context.rawRequest.headers[ORIGINAL_AUTH_HEADER];
				}
			}
			const tokenStatus = await checkTokens(req, context, options);
			if (tokenStatus.auth === "INVALID") {
				throw new HttpsError("unauthenticated", "Unauthenticated");
			}
			if (tokenStatus.app === "INVALID") {
				if (options.enforceAppCheck) {
					throw new HttpsError("unauthenticated", "Unauthenticated");
				} else {
					warn("Allowing request with invalid AppCheck token because enforcement is disabled");
				}
			}
			if (tokenStatus.app === "MISSING" && options.enforceAppCheck) {
				throw new HttpsError("unauthenticated", "Unauthenticated");
			}
			const instanceId = req.header("Firebase-Instance-ID-Token");
			if (instanceId) {
				context.instanceIdToken = req.header("Firebase-Instance-ID-Token");
			}
			const acceptsStreaming = req.header("accept") === "text/event-stream";
			if (acceptsStreaming && version === "gcfv1") {
				throw new HttpsError("invalid-argument", "Unsupported Accept header 'text/event-stream'");
			}
			const data = decode(req.body.data);
			if (options.authPolicy) {
				const authorized = await options.authPolicy(context.auth ?? null, data);
				if (!authorized) {
					throw new HttpsError("permission-denied", "Permission Denied");
				}
			}
			let result;
			if (version === "gcfv1") {
				result = await handler(data, context);
			} else {
				const arg = {
					...context,
					data,
					acceptsStreaming
				};
				const responseProxy = {
					sendChunk(chunk) {
						if (!acceptsStreaming) {
							return Promise.resolve(false);
						}
						if (abortController.signal.aborted) {
							return Promise.resolve(false);
						}
						const formattedData = encodeSSE({ message: chunk });
						let resolve;
						let reject;
						const p = new Promise((res$1, rej) => {
							resolve = res$1;
							reject = rej;
						});
						const wrote = res.write(formattedData, (error$1) => {
							if (error$1) {
								reject(error$1);
								return;
							}
							resolve(wrote);
						});
						if (wrote && heartbeatInterval !== null && heartbeatSeconds > 0) {
							scheduleHeartbeat();
						}
						return p;
					},
					signal: abortController.signal
				};
				if (acceptsStreaming) {
					res.status(200);
					if (heartbeatSeconds !== null && heartbeatSeconds > 0) {
						scheduleHeartbeat();
					}
				}
				result = await handler(arg, responseProxy);
				clearScheduledHeartbeat();
			}
			if (!abortController.signal.aborted) {
				result = encode(result);
				const responseBody = { result };
				if (acceptsStreaming) {
					res.write(encodeSSE(responseBody));
					res.end();
				} else {
					res.status(200).send(responseBody);
				}
			} else {
				res.end();
			}
		} catch (err) {
			if (!abortController.signal.aborted) {
				let httpErr = err;
				if (!(err instanceof HttpsError)) {
					error("Unhandled error", err);
					httpErr = new HttpsError("internal", "INTERNAL");
				}
				const { status } = httpErr.httpErrorCode;
				const body = { error: httpErr.toJSON() };
				if (version === "gcfv2" && req.header("accept") === "text/event-stream") {
					res.write(encodeSSE(body));
					res.end();
				} else {
					res.status(status).send(body);
				}
			} else {
				res.end();
			}
		} finally {
			clearScheduledHeartbeat();
		}
	};
}
/**
* Wraps an HTTP handler with a safety net for unhandled errors.
*
* This wrapper catches both synchronous errors and rejected Promises from `async` handlers.
* Without this, an unhandled error in an `async` handler would cause the request to hang
* until the platform timeout, as Express (v4) does not await handlers.
*
* It logs the error and returns a 500 Internal Server Error to the client if the response
* headers have not yet been sent.
*
* @internal
*/
function withErrorHandler(handler) {
	return async (req, res) => {
		try {
			await handler(req, res);
		} catch (err) {
			error("Unhandled error", err);
			if (!res.headersSent) {
				res.status(500).send("Internal Server Error");
			}
		}
	};
}

//#endregion
export { CALLABLE_AUTH_HEADER, DEFAULT_HEARTBEAT_SECONDS, HttpsError, ORIGINAL_AUTH_HEADER, checkAuthToken, decode, encode, isValidRequest, onCallHandler, unsafeDecodeAppCheckToken, unsafeDecodeIdToken, unsafeDecodeToken, withErrorHandler };