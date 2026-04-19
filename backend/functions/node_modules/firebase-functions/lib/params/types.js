const require_logger_index = require('../logger/index.js');

//#region src/params/types.ts
const EXPRESSION_TAG = Symbol.for("firebase-functions:Expression:Tag");
var Expression = class {
	/**
	* Handle the "Dual-Package Hazard" .
	*
	* We implement custom `Symbol.hasInstance` to so CJS/ESM Expression instances
	* are recognized as the same type.
	*/
	static [Symbol.hasInstance](instance) {
		return instance?.[EXPRESSION_TAG] === true;
	}
	get [EXPRESSION_TAG]() {
		return true;
	}
	/** Returns the expression's runtime value, based on the CLI's resolution of parameters. */
	value() {
		if (process.env.FUNCTIONS_CONTROL_API === "true") {
			require_logger_index.warn(`${this.toString()}.value() invoked during function deployment, instead of during runtime.`);
			require_logger_index.warn(`This is usually a mistake. In configs, use Params directly without calling .value().`);
			require_logger_index.warn(`example: { memory: memoryParam } not { memory: memoryParam.value() }`);
		}
		return this.runtimeValue();
	}
	/** @internal */
	runtimeValue() {
		throw new Error("Not implemented");
	}
	/** Returns the expression's representation as a braced CEL expression. */
	toCEL() {
		return `{{ ${this.toString()} }}`;
	}
	/** Returns the expression's representation as JSON. */
	toJSON() {
		return this.toString();
	}
};
function valueOf(arg) {
	return arg instanceof Expression ? arg.runtimeValue() : arg;
}
/**
* Returns how an entity (either an `Expression` or a literal value) should be represented in CEL.
* - Expressions delegate to the `.toString()` method, which is used by the WireManifest
* - Strings have to be quoted explicitly
* - Arrays are represented as []-delimited, parsable JSON
* - Numbers and booleans are not quoted explicitly
*/
function refOf(arg) {
	if (arg instanceof Expression) {
		return arg.toString();
	} else if (typeof arg === "string") {
		return `"${arg}"`;
	} else if (Array.isArray(arg)) {
		return JSON.stringify(arg);
	} else {
		return arg.toString();
	}
}
/**
* A CEL expression corresponding to a ternary operator, e.g {{ cond ? ifTrue : ifFalse }}
*/
var TernaryExpression = class extends Expression {
	constructor(test, ifTrue, ifFalse) {
		super();
		this.test = test;
		this.ifTrue = ifTrue;
		this.ifFalse = ifFalse;
		this.ifTrue = ifTrue;
		this.ifFalse = ifFalse;
	}
	/** @internal */
	runtimeValue() {
		return this.test.runtimeValue() ? valueOf(this.ifTrue) : valueOf(this.ifFalse);
	}
	toString() {
		return `${this.test} ? ${refOf(this.ifTrue)} : ${refOf(this.ifFalse)}`;
	}
};
/**
* A CEL expression that evaluates to boolean true or false based on a comparison
* between the value of another expression and a literal of that same type.
*/
var CompareExpression = class extends Expression {
	constructor(cmp, lhs, rhs) {
		super();
		this.cmp = cmp;
		this.lhs = lhs;
		this.rhs = rhs;
	}
	/** @internal */
	runtimeValue() {
		const left = this.lhs.runtimeValue();
		const right = valueOf(this.rhs);
		switch (this.cmp) {
			case "==": return Array.isArray(left) ? this.arrayEquals(left, right) : left === right;
			case "!=": return Array.isArray(left) ? !this.arrayEquals(left, right) : left !== right;
			case ">": return left > right;
			case ">=": return left >= right;
			case "<": return left < right;
			case "<=": return left <= right;
			default: throw new Error(`Unknown comparator ${this.cmp}`);
		}
	}
	/** @internal */
	arrayEquals(a, b) {
		return a.every((item) => b.includes(item)) && b.every((item) => a.includes(item));
	}
	toString() {
		const rhsStr = refOf(this.rhs);
		return `${this.lhs} ${this.cmp} ${rhsStr}`;
	}
	/** Returns a `TernaryExpression` which can resolve to one of two values, based on the resolution of this comparison. */
	thenElse(ifTrue, ifFalse) {
		return new TernaryExpression(this, ifTrue, ifFalse);
	}
};
/** Create a select input from a series of values or a map of labels to values */
function select(options) {
	let wireOpts;
	if (Array.isArray(options)) {
		wireOpts = options.map((opt) => ({ value: opt }));
	} else {
		wireOpts = Object.entries(options).map(([label, value]) => ({
			label,
			value
		}));
	}
	return { select: { options: wireOpts } };
}
/** Create a multi-select input from a series of values or map of labels to values. */
function multiSelect(options) {
	let wireOpts;
	if (Array.isArray(options)) {
		wireOpts = options.map((opt) => ({ value: opt }));
	} else {
		wireOpts = Object.entries(options).map(([label, value]) => ({
			label,
			value
		}));
	}
	return { multiSelect: { options: wireOpts } };
}
/**
* Autogenerate a list of buckets in a project that a user can select from.
*/
const BUCKET_PICKER = { resource: { type: "storage.googleapis.com/Bucket" } };
/**
* Represents a parametrized value that will be read from .env files if present,
* or prompted for by the CLI if missing. Instantiate these with the defineX
* methods exported by the firebase-functions/params namespace.
*/
var Param = class extends Expression {
	static {
		this.type = "string";
	}
	constructor(name, options = {}) {
		super();
		this.name = name;
		this.options = options;
	}
	/** @internal */
	runtimeValue() {
		throw new Error("Not implemented");
	}
	/** Returns a parametrized expression of Boolean type, based on comparing the value of this parameter to a literal or a different expression. */
	cmp(cmp, rhs) {
		return new CompareExpression(cmp, this, rhs);
	}
	/** Returns a parametrized expression of Boolean type, based on comparing the value of this parameter to a literal or a different expression. */
	equals(rhs) {
		return this.cmp("==", rhs);
	}
	/** Returns a parametrized expression of Boolean type, based on comparing the value of this parameter to a literal or a different expression. */
	notEquals(rhs) {
		return this.cmp("!=", rhs);
	}
	/** Returns a parametrized expression of Boolean type, based on comparing the value of this parameter to a literal or a different expression. */
	greaterThan(rhs) {
		return this.cmp(">", rhs);
	}
	/** Returns a parametrized expression of Boolean type, based on comparing the value of this parameter to a literal or a different expression. */
	greaterThanOrEqualTo(rhs) {
		return this.cmp(">=", rhs);
	}
	/** Returns a parametrized expression of Boolean type, based on comparing the value of this parameter to a literal or a different expression. */
	lessThan(rhs) {
		return this.cmp("<", rhs);
	}
	/** Returns a parametrized expression of Boolean type, based on comparing the value of this parameter to a literal or a different expression. */
	lessThanOrEqualTo(rhs) {
		return this.cmp("<=", rhs);
	}
	/**
	* Returns a parametrized expression of Boolean type, based on comparing the value of this parameter to a literal or a different expression.
	* @deprecated A typo. Use lessThanOrEqualTo instead.
	*/
	lessThanorEqualTo(rhs) {
		return this.lessThanOrEqualTo(rhs);
	}
	toString() {
		return `params.${this.name}`;
	}
	/** @internal */
	toSpec() {
		const { default: paramDefault,...otherOptions } = this.options;
		const out = {
			name: this.name,
			...otherOptions,
			type: this.constructor.type
		};
		if (paramDefault instanceof Expression) {
			out.default = paramDefault.toCEL();
		} else if (paramDefault !== undefined) {
			out.default = paramDefault;
		}
		if (out.input && "text" in out.input && out.input.text.validationRegex instanceof RegExp) {
			out.input.text.validationRegex = out.input.text.validationRegex.source;
		}
		return out;
	}
};
/**
* A parametrized string whose value is stored in Cloud Secret Manager
* instead of the local filesystem. Supply instances of SecretParams to
* the secrets array while defining a Function to make their values accessible
* during execution of that Function.
*/
var SecretParam = class {
	static {
		this.type = "secret";
	}
	constructor(name) {
		this.name = name;
	}
	/** @internal */
	runtimeValue() {
		const val = process.env[this.name];
		if (val === undefined) {
			require_logger_index.warn(`No value found for secret parameter "${this.name}". A function can only access a secret if you include the secret in the function's dependency array.`);
		}
		return val || "";
	}
	/** @internal */
	toSpec() {
		return {
			type: "secret",
			name: this.name
		};
	}
	/** Returns the secret's value at runtime. Throws an error if accessed during deployment. */
	value() {
		if (process.env.FUNCTIONS_CONTROL_API === "true") {
			throw new Error(`Cannot access the value of secret "${this.name}" during function deployment. Secret values are only available at runtime.`);
		}
		return this.runtimeValue();
	}
};
/**
* A parametrized object whose value is stored as a JSON string in Cloud Secret Manager.
* This is useful for managing groups of related configuration values, such as all settings
* for a third-party API, as a single unit. Supply instances of JsonSecretParam to the
* secrets array while defining a Function to make their values accessible during execution
* of that Function.
*/
var JsonSecretParam = class {
	static {
		this.type = "secret";
	}
	constructor(name) {
		this.name = name;
	}
	/** @internal */
	runtimeValue() {
		const val = process.env[this.name];
		if (val === undefined) {
			throw new Error(`No value found for secret parameter "${this.name}". A function can only access a secret if you include the secret in the function's dependency array.`);
		}
		try {
			return JSON.parse(val);
		} catch (error) {
			throw new Error(`"${this.name}" could not be parsed as JSON. Please verify its value in Secret Manager. Details: ${error}`);
		}
	}
	/** @internal */
	toSpec() {
		return {
			type: "secret",
			name: this.name,
			format: "json"
		};
	}
	/** Returns the secret's parsed JSON value at runtime. Throws an error if accessed during deployment, if the secret is not set, or if the value is not valid JSON. */
	value() {
		if (process.env.FUNCTIONS_CONTROL_API === "true") {
			throw new Error(`Cannot access the value of secret "${this.name}" during function deployment. Secret values are only available at runtime.`);
		}
		return this.runtimeValue();
	}
};
/**
*  A parametrized value of String type that will be read from .env files
*  if present, or prompted for by the CLI if missing.
*/
var StringParam = class extends Param {
	/** @internal */
	runtimeValue() {
		return process.env[this.name] || "";
	}
};
/**
* A CEL expression which represents an internal Firebase variable. This class
* cannot be instantiated by developers, but we provide several canned instances
* of it to make available parameters that will never have to be defined at
* deployment time, and can always be read from process.env.
* @internal
*/
var InternalExpression = class extends Param {
	constructor(name, getter) {
		super(name);
		this.getter = getter;
	}
	/** @internal */
	runtimeValue() {
		return this.getter(process.env) || "";
	}
	toSpec() {
		throw new Error("An InternalExpression should never be marshalled for wire transmission.");
	}
};
/**
*  A parametrized value of Integer type that will be read from .env files
*  if present, or prompted for by the CLI if missing.
*/
var IntParam = class extends Param {
	static {
		this.type = "int";
	}
	/** @internal */
	runtimeValue() {
		return parseInt(process.env[this.name] || "0", 10) || 0;
	}
};
/**
*  A parametrized value of Float type that will be read from .env files
*  if present, or prompted for by the CLI if missing.
*/
var FloatParam = class extends Param {
	static {
		this.type = "float";
	}
	/** @internal */
	runtimeValue() {
		return parseFloat(process.env[this.name] || "0") || 0;
	}
};
/**
*  A parametrized value of Boolean type that will be read from .env files
*  if present, or prompted for by the CLI if missing.
*/
var BooleanParam = class extends Param {
	static {
		this.type = "boolean";
	}
	/** @internal */
	runtimeValue() {
		return !!process.env[this.name] && process.env[this.name] === "true";
	}
	/** @deprecated */
	then(ifTrue, ifFalse) {
		return this.thenElse(ifTrue, ifFalse);
	}
	thenElse(ifTrue, ifFalse) {
		return new TernaryExpression(this, ifTrue, ifFalse);
	}
};
/**
*  A parametrized value of String[] type that will be read from .env files
*  if present, or prompted for by the CLI if missing.
*/
var ListParam = class extends Param {
	static {
		this.type = "list";
	}
	/** @internal */
	runtimeValue() {
		const val = JSON.parse(process.env[this.name]);
		if (!Array.isArray(val) || !val.every((v) => typeof v === "string")) {
			return [];
		}
		return val;
	}
	/** @hidden */
	greaterThan(rhs) {
		throw new Error(">/< comparison operators not supported on params of type List");
	}
	/** @hidden */
	greaterThanOrEqualTo(rhs) {
		throw new Error(">/< comparison operators not supported on params of type List");
	}
	/** @hidden */
	lessThan(rhs) {
		throw new Error(">/< comparison operators not supported on params of type List");
	}
	/** @hidden */
	lessThanorEqualTo(rhs) {
		throw new Error(">/< comparison operators not supported on params of type List");
	}
};

//#endregion
exports.BUCKET_PICKER = BUCKET_PICKER;
exports.BooleanParam = BooleanParam;
exports.CompareExpression = CompareExpression;
exports.Expression = Expression;
exports.FloatParam = FloatParam;
exports.IntParam = IntParam;
exports.InternalExpression = InternalExpression;
exports.JsonSecretParam = JsonSecretParam;
exports.ListParam = ListParam;
exports.Param = Param;
exports.SecretParam = SecretParam;
exports.StringParam = StringParam;
exports.TernaryExpression = TernaryExpression;
exports.multiSelect = multiSelect;
exports.select = select;