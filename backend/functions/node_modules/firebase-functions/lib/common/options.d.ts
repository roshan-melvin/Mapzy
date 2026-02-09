declare const RESET_VALUE_TAG: unique symbol;
/**
 * Special configuration type to reset configuration to platform default.
 *
 * @alpha
 */
export declare class ResetValue {
    /**
     * Handle the "Dual-Package Hazard".
     *
     * We implement custom `Symbol.hasInstance` to so CJS/ESM ResetValue instances
     * are recognized as the same type.
     */
    static [Symbol.hasInstance](instance: unknown): boolean;
    get [RESET_VALUE_TAG](): boolean;
    toJSON(): null;
    private constructor();
    static getInstance(): ResetValue;
}
/**
 * Special configuration value to reset configuration to platform default.
 */
export declare const RESET_VALUE: ResetValue;
export {};
