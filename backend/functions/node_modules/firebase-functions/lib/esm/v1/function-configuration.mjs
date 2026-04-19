import { RESET_VALUE } from "../common/options.mjs";

//#region src/v1/function-configuration.ts
/**
* List of all regions supported by Cloud Functions.
*/
const SUPPORTED_REGIONS = [
	"us-central1",
	"us-east1",
	"us-east4",
	"us-west2",
	"us-west3",
	"us-west4",
	"europe-central2",
	"europe-west1",
	"europe-west2",
	"europe-west3",
	"europe-west6",
	"asia-east1",
	"asia-east2",
	"asia-northeast1",
	"asia-northeast2",
	"asia-northeast3",
	"asia-south1",
	"asia-southeast1",
	"asia-southeast2",
	"northamerica-northeast1",
	"southamerica-east1",
	"australia-southeast1"
];
/**
* Cloud Functions min timeout value.
*/
const MIN_TIMEOUT_SECONDS = 0;
/**
* Cloud Functions max timeout value.
*/
const MAX_TIMEOUT_SECONDS = 540;
/**
* List of available memory options supported by Cloud Functions.
*/
const VALID_MEMORY_OPTIONS = [
	"128MB",
	"256MB",
	"512MB",
	"1GB",
	"2GB",
	"4GB",
	"8GB"
];
/**
* List of available options for VpcConnectorEgressSettings.
*/
const VPC_EGRESS_SETTINGS_OPTIONS = [
	"VPC_CONNECTOR_EGRESS_SETTINGS_UNSPECIFIED",
	"PRIVATE_RANGES_ONLY",
	"ALL_TRAFFIC"
];
/**
* List of available options for IngressSettings.
*/
const INGRESS_SETTINGS_OPTIONS = [
	"INGRESS_SETTINGS_UNSPECIFIED",
	"ALLOW_ALL",
	"ALLOW_INTERNAL_ONLY",
	"ALLOW_INTERNAL_AND_GCLB"
];
const DEFAULT_FAILURE_POLICY = { retry: {} };
const MAX_NUMBER_USER_LABELS = 58;

//#endregion
export { DEFAULT_FAILURE_POLICY, INGRESS_SETTINGS_OPTIONS, MAX_NUMBER_USER_LABELS, MAX_TIMEOUT_SECONDS, MIN_TIMEOUT_SECONDS, RESET_VALUE, SUPPORTED_REGIONS, VALID_MEMORY_OPTIONS, VPC_EGRESS_SETTINGS_OPTIONS };