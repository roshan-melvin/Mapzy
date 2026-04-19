import { __export } from "../_virtual/rolldown_runtime.mjs";
import { BUCKET_PICKER, BooleanParam, Expression, FloatParam, IntParam, InternalExpression, JsonSecretParam, ListParam, SecretParam, StringParam, multiSelect, select } from "./types.mjs";

//#region src/params/index.ts
var params_exports = /* @__PURE__ */ __export({
	BUCKET_PICKER: () => BUCKET_PICKER,
	Expression: () => Expression,
	clearParams: () => clearParams,
	databaseURL: () => databaseURL,
	declaredParams: () => declaredParams,
	defineBoolean: () => defineBoolean,
	defineFloat: () => defineFloat,
	defineInt: () => defineInt,
	defineJsonSecret: () => defineJsonSecret,
	defineList: () => defineList,
	defineSecret: () => defineSecret,
	defineString: () => defineString,
	gcloudProject: () => gcloudProject,
	multiSelect: () => multiSelect,
	projectID: () => projectID,
	select: () => select,
	storageBucket: () => storageBucket
});
/**
* Use a global singleton to manage the list of declared parameters.
*
* This ensures that parameters are shared between CJS and ESM builds,
* avoiding the "dual-package hazard" where the src/bin/firebase-functions.ts (CJS) sees
* an empty list while the user's code (ESM) populates a different list.
*/
const majorVersion = typeof "7" !== "undefined" ? "7" : "0";
const GLOBAL_SYMBOL = Symbol.for(`firebase-functions:params:declaredParams:v${majorVersion}`);
const globalSymbols = globalThis;
if (!globalSymbols[GLOBAL_SYMBOL]) {
	globalSymbols[GLOBAL_SYMBOL] = [];
}
const declaredParams = globalSymbols[GLOBAL_SYMBOL];
/**
* Use a helper to manage the list such that parameters are uniquely
* registered once only but order is preserved.
* @internal
*/
function registerParam(param) {
	for (let i = 0; i < declaredParams.length; i++) {
		if (declaredParams[i].name === param.name) {
			declaredParams.splice(i, 1);
		}
	}
	declaredParams.push(param);
}
/**
* For testing.
* @internal
*/
function clearParams() {
	declaredParams.splice(0, declaredParams.length);
}
/**
* A built-in parameter that resolves to the default RTDB database URL associated
* with the project, without prompting the deployer. Empty string if none exists.
*/
const databaseURL = new InternalExpression("DATABASE_URL", (env) => JSON.parse(env.FIREBASE_CONFIG)?.databaseURL || "");
/**
* A built-in parameter that resolves to the Cloud project ID associated with
* the project, without prompting the deployer.
*/
const projectID = new InternalExpression("PROJECT_ID", (env) => JSON.parse(env.FIREBASE_CONFIG)?.projectId || "");
/**
* A built-in parameter that resolves to the Cloud project ID, without prompting
* the deployer.
*/
const gcloudProject = new InternalExpression("GCLOUD_PROJECT", (env) => JSON.parse(env.FIREBASE_CONFIG)?.projectId || "");
/**
* A builtin parameter that resolves to the Cloud storage bucket associated
* with the function, without prompting the deployer. Empty string if not
* defined.
*/
const storageBucket = new InternalExpression("STORAGE_BUCKET", (env) => JSON.parse(env.FIREBASE_CONFIG)?.storageBucket || "");
/**
* Declares a secret param, that will persist values only in Cloud Secret Manager.
* Secrets are stored internally as bytestrings. Use `ParamOptions.as` to provide type
* hinting during parameter resolution.
*
* @param name The name of the environment variable to use to load the parameter.
* @returns A parameter with a `string` return type for `.value`.
*/
function defineSecret(name) {
	const param = new SecretParam(name);
	registerParam(param);
	return param;
}
/**
* Declares a secret parameter that retrieves a structured JSON object in Cloud Secret Manager.
* This is useful for managing groups of related configuration values, such as all settings
* for a third-party API, as a single unit.
*
* The secret value must be a valid JSON string. At runtime, the value will be automatically parsed
* and returned as a JavaScript object. If the value is not set or is not valid JSON, an error will be thrown.
*
* @param name The name of the environment variable to use to load the parameter.
* @returns A parameter whose `.value()` method returns the parsed JSON object.
* ```
*/
function defineJsonSecret(name) {
	const param = new JsonSecretParam(name);
	registerParam(param);
	return param;
}
/**
* Declare a string parameter.
*
* @param name The name of the environment variable to use to load the parameter.
* @param options Configuration options for the parameter.
* @returns A parameter with a `string` return type for `.value`.
*/
function defineString(name, options = {}) {
	const param = new StringParam(name, options);
	registerParam(param);
	return param;
}
/**
* Declare a boolean parameter.
*
* @param name The name of the environment variable to use to load the parameter.
* @param options Configuration options for the parameter.
* @returns A parameter with a `boolean` return type for `.value`.
*/
function defineBoolean(name, options = {}) {
	const param = new BooleanParam(name, options);
	registerParam(param);
	return param;
}
/**
* Declare an integer parameter.
*
* @param name The name of the environment variable to use to load the parameter.
* @param options Configuration options for the parameter.
* @returns A parameter with a `number` return type for `.value`.
*/
function defineInt(name, options = {}) {
	const param = new IntParam(name, options);
	registerParam(param);
	return param;
}
/**
* Declare a float parameter.
*
* @param name The name of the environment variable to use to load the parameter.
* @param options Configuration options for the parameter.
* @returns A parameter with a `number` return type for `.value`.
*
* @internal
*/
function defineFloat(name, options = {}) {
	const param = new FloatParam(name, options);
	registerParam(param);
	return param;
}
/**
* Declare a list parameter.
*
* @param name The name of the environment variable to use to load the parameter.
* @param options Configuration options for the parameter.
* @returns A parameter with a `string[]` return type for `.value`.
*/
function defineList(name, options = {}) {
	const param = new ListParam(name, options);
	registerParam(param);
	return param;
}

//#endregion
export { BUCKET_PICKER, Expression, clearParams, databaseURL, declaredParams, defineBoolean, defineFloat, defineInt, defineJsonSecret, defineList, defineSecret, defineString, gcloudProject, multiSelect, params_exports, projectID, select, storageBucket };