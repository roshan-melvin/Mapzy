import { __require } from "../_virtual/rolldown_runtime.mjs";
import { declaredParams } from "../params/index.mjs";
import * as path from "path";
import * as url from "url";

//#region src/runtime/loader.ts
/**
* Dynamically load import function to prevent TypeScript from
* transpiling into a require.
*
* See https://github.com/microsoft/TypeScript/issues/43329.
*
*/
const dynamicImport = new Function("modulePath", "return import(modulePath)");
async function loadModule(functionsDir) {
	const absolutePath = path.resolve(functionsDir);
	try {
		return __require(path.resolve(absolutePath));
	} catch (e) {
		if (e.code === "ERR_REQUIRE_ESM" || e.code === "ERR_REQUIRE_ASYNC_MODULE") {
			const modulePath = __require.resolve(absolutePath);
			const moduleURL = url.pathToFileURL(modulePath).href;
			return await dynamicImport(moduleURL);
		}
		throw e;
	}
}
function extractStack(module, endpoints, requiredAPIs, extensions, prefix = "") {
	for (const [name, valAsUnknown] of Object.entries(module)) {
		const val = valAsUnknown;
		if (typeof val === "function" && val.__endpoint && typeof val.__endpoint === "object") {
			const funcName = prefix + name;
			endpoints[funcName] = {
				...val.__endpoint,
				entryPoint: funcName.replace(/-/g, ".")
			};
			if (val.__requiredAPIs && Array.isArray(val.__requiredAPIs)) {
				requiredAPIs.push(...val.__requiredAPIs);
			}
		} else if (isFirebaseRefExtension(val)) {
			extensions[val.instanceId] = {
				params: convertExtensionParams(val.params),
				ref: val.FIREBASE_EXTENSION_REFERENCE,
				events: val.events || []
			};
		} else if (isFirebaseLocalExtension(val)) {
			extensions[val.instanceId] = {
				params: convertExtensionParams(val.params),
				localPath: val.FIREBASE_EXTENSION_LOCAL_PATH,
				events: val.events || []
			};
		} else if (isObject(val)) {
			extractStack(val, endpoints, requiredAPIs, extensions, prefix + name + "-");
		}
	}
}
function toTitleCase(txt) {
	return txt.charAt(0).toUpperCase() + txt.substring(1).toLowerCase();
}
function snakeToCamelCase(txt) {
	let ret = txt.toLowerCase();
	ret = ret.replace(/_/g, " ");
	ret = ret.replace(/\w\S*/g, toTitleCase);
	ret = ret.charAt(0).toLowerCase() + ret.substring(1);
	return ret;
}
function convertExtensionParams(params) {
	const systemPrefixes = {
		FUNCTION: "firebaseextensions.v1beta.function",
		V2FUNCTION: "firebaseextensions.v1beta.v2function"
	};
	const converted = {};
	for (const [rawKey, paramVal] of Object.entries(params)) {
		let key = rawKey;
		if (rawKey.startsWith("_") && rawKey !== "_EVENT_ARC_REGION") {
			const prefix = rawKey.substring(1).split("_")[0];
			const suffix = rawKey.substring(2 + prefix.length);
			key = `${systemPrefixes[prefix]}/${snakeToCamelCase(suffix)}`;
		}
		if (Array.isArray(paramVal)) {
			converted[key] = paramVal.join(",");
		} else {
			converted[key] = paramVal;
		}
	}
	return converted;
}
function isObject(value) {
	return typeof value === "object" && value !== null;
}
const isFirebaseLocalExtension = (val) => {
	return isObject(val) && typeof val.FIREBASE_EXTENSION_LOCAL_PATH === "string" && typeof val.instanceId === "string" && isObject(val.params) && (!val.events || Array.isArray(val.events));
};
const isFirebaseRefExtension = (val) => {
	return isObject(val) && typeof val.FIREBASE_EXTENSION_REFERENCE === "string" && typeof val.instanceId === "string" && isObject(val.params) && (!val.events || Array.isArray(val.events));
};
function mergeRequiredAPIs(requiredAPIs) {
	const apiToReasons = {};
	for (const { api, reason } of requiredAPIs) {
		const reasons = apiToReasons[api] || new Set();
		reasons.add(reason);
		apiToReasons[api] = reasons;
	}
	const merged = [];
	for (const [api, reasons] of Object.entries(apiToReasons)) {
		merged.push({
			api,
			reason: Array.from(reasons).join(" ")
		});
	}
	return merged;
}
async function loadStack(functionsDir) {
	const endpoints = {};
	const requiredAPIs = [];
	const extensions = {};
	const mod = await loadModule(functionsDir);
	extractStack(mod, endpoints, requiredAPIs, extensions);
	const stack = {
		endpoints,
		specVersion: "v1alpha1",
		requiredAPIs: mergeRequiredAPIs(requiredAPIs),
		extensions
	};
	if (declaredParams.length > 0) {
		stack.params = declaredParams.map((p) => p.toSpec());
	}
	return stack;
}

//#endregion
export { extractStack, loadStack, mergeRequiredAPIs };