import React, { useEffect, useState } from "react";
import {
  AppState,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { androidVault } from "./autofill";

export function AutofillSettings() {
  const [enabled, setEnabled] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const refresh = () => {
      void androidVault
        ?.isEnabled()
        .then(setEnabled)
        .catch(() => setError("No se pudo consultar el servicio."));
    };
    refresh();
    const subscription = AppState.addEventListener("change", (state) => {
      if (state === "active") refresh();
    });
    return () => subscription.remove();
  }, []);
  if (Platform.OS !== "android")
    return (
      <View style={styles.card}>
        <Text style={styles.title}>Autocompletar en iOS</Text>
        <Text style={styles.body}>
          La extensión de autocompletado para iPhone aún no está implementada.
          Puedes gestionar y copiar tus credenciales desde la bóveda.
        </Text>
      </View>
    );
  return (
    <View style={styles.card}>
      <Text style={styles.title}>Autocompletar en Android</Text>
      <Text style={styles.body}>
        {!androidVault
          ? "Esta instalación no incluye el servicio de autocompletado. Instala una compilación Android actualizada de Clave."
          : enabled
            ? "Clave está seleccionado como servicio de autocompletado."
            : "Selecciona Clave como gestor de contraseñas en los ajustes de Android."}
      </Text>
      {!!androidVault && (
        <Pressable
          accessibilityRole="button"
          style={styles.button}
          onPress={() => {
            void androidVault
              ?.enable()
              .catch(() =>
                setError(
                  "Abre Ajustes de Android → Contraseñas y autocompletar → Clave.",
                ),
              );
          }}
        >
          <Text style={styles.buttonText}>
            {enabled ? "Abrir ajustes de autocompletado" : "Activar Clave"}
          </Text>
        </Pressable>
      )}
      <Text style={styles.body}>
        En Chrome, activa el uso de otros servicios de autocompletado si aparece
        esa opción. Toca un campo de contraseña y elige Clave para generar o
        seleccionar una cuenta. Algunos sitios no exponen sus campos al sistema.
      </Text>
      {!!error && (
        <Text accessibilityLiveRegion="polite" style={styles.body}>
          {error}
        </Text>
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  card: {
    padding: 20,
    gap: 14,
    borderRadius: 16,
    backgroundColor: "#1C1F26",
    borderWidth: 1,
    borderColor: "#343840",
  },
  title: { color: "#F4F4F5", fontSize: 18, fontWeight: "600" },
  body: { color: "#A1A1AA", fontSize: 14, lineHeight: 22 },
  button: {
    padding: 14,
    borderRadius: 10,
    backgroundColor: "#E5E7EB",
    alignItems: "center",
  },
  buttonText: { color: "#111318", fontWeight: "600" },
});
