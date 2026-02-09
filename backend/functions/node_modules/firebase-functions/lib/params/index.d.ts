/**
 * @hidden
 * @alpha
 */
import { BooleanParam, Expression, IntParam, Param, ParamOptions, SecretParam, JsonSecretParam, StringParam, ListParam } from "./types";
export { BUCKET_PICKER, select, multiSelect } from "./types";
export type { TextInput, SelectInput, SelectOptions, MultiSelectInput, Param, SecretParam, JsonSecretParam, StringParam, BooleanParam, IntParam, ListParam, } from "./types";
export { Expression };
export type { ParamOptions };
type SecretOrExpr = Param<any> | SecretParam | JsonSecretParam<any>;
export declare const declaredParams: SecretOrExpr[];
/**
 * A built-in parameter that resolves to the default RTDB database URL associated
 * with the project, without prompting the deployer. Empty string if none exists.
 */
export declare const databaseURL: Param<string>;
/**
 * A built-in parameter that resolves to the Cloud project ID associated with
 * the project, without prompting the deployer.
 */
export declare const projectID: Param<string>;
/**
 * A built-in parameter that resolves to the Cloud project ID, without prompting
 * the deployer.
 */
export declare const gcloudProject: Param<string>;
/**
 * A builtin parameter that resolves to the Cloud storage bucket associated
 * with the function, without prompting the deployer. Empty string if not
 * defined.
 */
export declare const storageBucket: Param<string>;
/**
 * Declares a secret param, that will persist values only in Cloud Secret Manager.
 * Secrets are stored internally as bytestrings. Use `ParamOptions.as` to provide type
 * hinting during parameter resolution.
 *
 * @param name The name of the environment variable to use to load the parameter.
 * @returns A parameter with a `string` return type for `.value`.
 */
export declare function defineSecret(name: string): SecretParam;
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
export declare function defineJsonSecret<T = any>(name: string): JsonSecretParam<T>;
/**
 * Declare a string parameter.
 *
 * @param name The name of the environment variable to use to load the parameter.
 * @param options Configuration options for the parameter.
 * @returns A parameter with a `string` return type for `.value`.
 */
export declare function defineString(name: string, options?: ParamOptions<string>): StringParam;
/**
 * Declare a boolean parameter.
 *
 * @param name The name of the environment variable to use to load the parameter.
 * @param options Configuration options for the parameter.
 * @returns A parameter with a `boolean` return type for `.value`.
 */
export declare function defineBoolean(name: string, options?: ParamOptions<boolean>): BooleanParam;
/**
 * Declare an integer parameter.
 *
 * @param name The name of the environment variable to use to load the parameter.
 * @param options Configuration options for the parameter.
 * @returns A parameter with a `number` return type for `.value`.
 */
export declare function defineInt(name: string, options?: ParamOptions<number>): IntParam;
/**
 * Declare a list parameter.
 *
 * @param name The name of the environment variable to use to load the parameter.
 * @param options Configuration options for the parameter.
 * @returns A parameter with a `string[]` return type for `.value`.
 */
export declare function defineList(name: string, options?: ParamOptions<string[]>): ListParam;
