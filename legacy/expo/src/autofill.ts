import { Platform } from "react-native";
import { requireOptionalNativeModule } from "expo";

export interface AndroidVault {
  hasVault(): Promise<boolean>;
  open(create: boolean): Promise<string>;
  save(json: string): Promise<string>;
  isEnabled(): Promise<boolean>;
  enable(): Promise<void>;
}
export const androidVault =
  Platform.OS === "android"
    ? requireOptionalNativeModule<AndroidVault>("ClaveAutofill")
    : null;
