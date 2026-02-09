import { CloudEvent, CloudFunction } from "../../core";
import { ParamsOf, VarName } from "../../../common/params";
import { EventHandlerOptions, SupportedRegion } from "../../options";
import { Expression } from "../../../params";
import { ResetValue } from "../../../common/options";
/** @hidden */
export interface SourceLocation {
    line: number;
    column: number;
}
/** @hidden */
export interface GraphqlErrorExtensions {
    file: string;
    code: string;
    debugDetails: string;
}
/** @hidden */
export interface GraphqlError {
    message: string;
    locations: Array<SourceLocation>;
    path: Array<string>;
    extensions: GraphqlErrorExtensions;
}
/** @hidden */
export interface RawMutation<V, R> {
    data: R;
    variables: V;
    errors: Array<GraphqlError>;
}
/** @hidden */
export interface MutationEventData<V, R> {
    ["@type"]: "type.googleapis.com/google.events.firebase.dataconnect.v1.MutationEventData";
    payload: RawMutation<V, R>;
}
/** @hidden */
export interface RawDataConnectEvent<T> extends CloudEvent<T> {
    project: string;
    location: string;
    service: string;
    schema: string;
    connector: string;
    operation: string;
    authtype: AuthType;
    authid?: string;
}
/**
 * AuthType defines the possible values for the authType field in a Firebase Data Connect event.
 * - app_user: an end user of an application..
 * - admin: an admin user of an application. In the context of impersonate endpoints used by the admin SDK, the impersonator.
 * - unknown: a general type to capture all other principals not captured in the other auth types.
 */
export type AuthType = "app_user" | "admin" | "unknown";
/** OperationOptions extend EventHandlerOptions with a provided service, connector, and operation. */
export interface OperationOptions<Service extends string = string, Connector extends string = string, Operation extends string = string> extends EventHandlerOptions {
    /** Firebase Data Connect service ID */
    service?: Service;
    /** Firebase Data Connect connector ID */
    connector?: Connector;
    /** Name of the operation */
    operation?: Operation;
    /**
     * Region where functions should be deployed. Defaults to us-central1.
     */
    region?: SupportedRegion | string | Expression<string> | ResetValue;
}
export type DataConnectParams<PathPatternOrOptions extends string | OperationOptions> = PathPatternOrOptions extends string ? ParamsOf<PathPatternOrOptions> : PathPatternOrOptions extends OperationOptions<infer Service extends string, infer Connector extends string, infer Operation extends string> ? Record<VarName<Service> | VarName<Connector> | VarName<Operation>, string> : never;
export interface DataConnectEvent<T, Params extends Record<never, string>> extends CloudEvent<T> {
    /** The location of the Firebase Data Connect instance */
    location: string;
    /** The project identifier */
    project: string;
    /**
     * An object containing the values of the path patterns.
     * Only named capture groups will be populated - {key}, {key=*}, {key=**}.
     */
    params: Params;
    /** The type of principal that triggered the event */
    authType: AuthType;
    /** The unique identifier for the principal */
    authId?: string;
}
/**
 * Event handler that triggers when a mutation is executed in Firebase Data Connect.
 *
 * @param mutation - The mutation path to trigger on.
 * @param handler - Event handler which is run every time a mutation is executed.
 */
export declare function onMutationExecuted<Mutation extends string, Variables = unknown, ResponseData = unknown>(mutation: Mutation, handler: (event: DataConnectEvent<MutationEventData<Variables, ResponseData>, DataConnectParams<Mutation>>) => unknown | Promise<unknown>): CloudFunction<DataConnectEvent<MutationEventData<Variables, ResponseData>, DataConnectParams<Mutation>>>;
/**
 * Event handler that triggers when a mutation is executed in Firebase Data Connect.
 *
 * @param opts - Options that can be set on an individual event-handling function.
 * @param handler - Event handler which is run every time a mutation is executed.
 */
export declare function onMutationExecuted<Options extends OperationOptions, Variables = unknown, ResponseData = unknown>(opts: Options, handler: (event: DataConnectEvent<MutationEventData<Variables, ResponseData>, DataConnectParams<Options>>) => unknown | Promise<unknown>): CloudFunction<DataConnectEvent<MutationEventData<Variables, ResponseData>, DataConnectParams<Options>>>;
