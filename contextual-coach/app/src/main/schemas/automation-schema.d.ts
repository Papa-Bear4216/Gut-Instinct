// TypeScript definitions for Gut-Instinct Automation Schema

export interface Trigger {
  // Discriminated union
}

export interface AppTransitionTrigger {
  type: "app_transition";
  fromPackage: string;
  toPackage: string;
  minCount: number;
  maxGapMs: number;
}

export interface MlModelTrigger {
  type: "ml_model";
  modelName: string;
  outputKey: string;
  threshold: number;
  comparison: "gt" | "gte" | "lt" | "lte" | "eq";
}

export interface TimeTrigger {
  type: "time";
  schedule: TimeSchedule;
}

export interface TimeSchedule {
  // Discriminated union
  times?: string[]; // HH:mm format
  everyMs?: number; // milliseconds
}

exportinterface Action {
  // Discriminated union
}

export interface LaunchAppAction {
  type: "launch_app";
  package: string;
}

export interface OpenUrlAction {
  type: "open_url";
  url: string; // URI format
}

export interface CopyTextAction {
  type: "copy_text";
  text: string;
}

export interface ShowNotificationAction {
  type: "show_notification";
  title: string;
  message: string;
}

export interface RunScriptAction {
  type: "run_script";
  script: string;
}

export interface SafetyGate {
  // Discriminated union
}

export interface TimeWindowGate {
  type: "time_window";
  startTime: string; // HH:mm
  endTime: string; // HH:mm
}

export interface BatteryLevelGate {
  type: "battery_level";
  minLevel: number; // 0-100
}

export interface ScreenOnGate {
  type: "screen_on";
}

export interface UserPresentGate {
  type: "user_present";
}

export interface CustomMLGate {
  type: "custom_ml";
  modelName: string;
  outputKey: string;
  threshold: number;
  comparison: "gt" | "gte" | "lt" | "lte" | "eq";
}

export interface Automation {
  id: string;
  name?: string;
  description?: string;
  trigger: Trigger;
  actions: Action[];
  safetyGates?: SafetyGate[];
  cooldownMs?: number;
  enabled?: boolean;
  createdAt?: number;
  modifiedAt?: number;
}

// Specific type aliases for union types
export type TriggerType = AppTransitionTrigger | MlModelTrigger | TimeTrigger;
export type ActionType = LaunchAppAction | OpenUrlAction | CopyTextAction | ShowNotificationAction | RunScriptAction;
export type SafetyGateType = TimeWindowGate | BatteryLevelGate | ScreenOnGate | UserPresentGate | CustomMLGate;

// Helper type for Discriminated unions
export type Trigger = TriggerType;
export type Action = ActionType;
export type SafetyGate = SafetyGateType;