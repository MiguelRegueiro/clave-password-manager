import React, { useEffect, useRef, useState } from "react";
import {
  Alert,
  AppState,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import * as Crypto from "expo-crypto";
import * as Clipboard from "expo-clipboard";
import { usePreventScreenCapture } from "expo-screen-capture";
import {
  createVault,
  hasVault,
  openVault,
  saveVault,
  isExpoGo,
  type Credential,
  type Vault,
} from "./src/vault";
import { generatePassword, normalizeWebsite } from "./src/password.mjs";
import { AutofillSettings } from "./src/AutofillSettings";
const colors = {
  bg: "#111318",
  panel: "#1C1F26",
  muted: "#A1A1AA",
  text: "#F4F4F5",
  accent: "#E5E7EB",
  line: "#343840",
};
type Page = "vault" | "generator" | "settings";
const emptyEntry = (profile = "Ocio"): Credential => ({
  id: "",
  title: "",
  website: "",
  username: "",
  password: "",
  profile,
  updatedAt: "",
});
function Button({
  label,
  onPress,
  secondary = false,
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  secondary?: boolean;
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        s.button,
        secondary && s.secondary,
        (pressed || disabled) && { opacity: 0.5 },
      ]}
    >
      <Text style={[s.buttonText, secondary && { color: colors.text }]}>
        {label}
      </Text>
    </Pressable>
  );
}
function Field({
  label,
  value,
  onChangeText,
  secret = false,
  ...props
}: {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  secret?: boolean;
  placeholder?: string;
  keyboardType?: "default" | "url" | "email-address";
  maxLength?: number;
}) {
  return (
    <View style={s.field}>
      <Text style={s.label}>{label}</Text>
      <TextInput
        {...props}
        value={value}
        onChangeText={onChangeText}
        secureTextEntry={secret}
        autoCapitalize="none"
        autoCorrect={false}
        autoComplete="off"
        placeholderTextColor="#71717A"
        style={s.input}
        accessibilityLabel={label}
      />
    </View>
  );
}
function CaptureProtection() {
  usePreventScreenCapture();
  return null;
}
function VaultApp() {
  const [exists, setExists] = useState<boolean | null>(null);
  const [vault, setVault] = useState<Vault | null>(null);
  const [page, setPage] = useState<Page>("vault");
  const [profile, setProfile] = useState("Todos");
  const [search, setSearch] = useState("");
  const [draft, setDraft] = useState<Credential | null>(null);
  const [detail, setDetail] = useState<Credential | null>(null);
  const [visible, setVisible] = useState(false);
  const [generated, setGenerated] = useState("");
  const [length, setLength] = useState(20);
  const [symbols, setSymbols] = useState(true);
  const [newProfile, setNewProfile] = useState("");
  const [busy, setBusy] = useState(false);
  const [covered, setCovered] = useState(false);
  const [notice, setNotice] = useState("");
  const generation = useRef(0);
  const working = useRef(false);
  const clipboardTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const copied = useRef<string | null>(null);
  const clipboardExpiry = useRef(0);
  const inactivity = useRef<ReturnType<typeof setTimeout> | null>(null);
  async function clearClipboard() {
    if (clipboardTimer.current) clearTimeout(clipboardTimer.current);
    const previous = copied.current;
    if (previous !== null) {
      try {
        if ((await Clipboard.getStringAsync()) === previous)
          await Clipboard.setStringAsync("");
        if (copied.current === previous) copied.current = null;
      } catch {
        /* Retry on foreground if the OS restricted background access. */
      }
    }
  }
  function lock(clearCopied = true) {
    generation.current++;
    setVault(null);
    setDraft(null);
    setDetail(null);
    setGenerated("");
    setVisible(false);
    setSearch("");
    setNewProfile("");
    setNotice("");
    if (inactivity.current) clearTimeout(inactivity.current);
    if (clearCopied) void clearClipboard();
  }
  function touch() {
    if (inactivity.current) clearTimeout(inactivity.current);
    if (vault) inactivity.current = setTimeout(lock, 120000);
  }
  useEffect(() => {
    hasVault()
      .then(setExists)
      .catch(() => {
        setNotice("No se pudo leer el almacenamiento. Reinicia la app.");
      });
    const listener = AppState.addEventListener("change", (state) => {
      setCovered(state !== "active");
      // Preserve the short clipboard window so the user can paste in another app.
      if (state === "background") lock(false);
      if (state === "active" && Date.now() >= clipboardExpiry.current)
        void clearClipboard();
    });
    return () => {
      listener.remove();
      if (inactivity.current) clearTimeout(inactivity.current);
      void clearClipboard();
    };
  }, []);
  useEffect(() => {
    touch();
  }, [vault]);
  useEffect(() => {
    if (!detail) return;
    const timer = setTimeout(() => {
      setDetail(null);
      setVisible(false);
    }, 30000);
    return () => clearTimeout(timer);
  }, [detail]);
  async function perform(action: () => Promise<void>) {
    if (working.current) return;
    working.current = true;
    setBusy(true);
    setNotice("");
    try {
      await action();
    } catch (error) {
      setNotice(
        error instanceof Error
          ? error.message
          : "No se pudo completar la operación.",
      );
    } finally {
      working.current = false;
      setBusy(false);
    }
  }
  async function unlock() {
    const current = generation.current;
    await perform(async () => {
      const value = exists ? await openVault() : await createVault();
      setExists(true);
      if (current === generation.current) {
        setVault(value);
        setPage("vault");
      }
    });
  }
  async function persist(next: Vault, done: () => void) {
    const current = generation.current;
    await perform(async () => {
      await saveVault(next);
      if (current === generation.current) {
        setVault(next);
        done();
        setNotice("Cambios guardados.");
      }
    });
  }
  async function viewEntry(id: string) {
    const current = generation.current;
    await perform(async () => {
      const fresh = await openVault();
      if (current === generation.current) {
        setVault(fresh);
        setDetail(fresh.credentials.find((item) => item.id === id) ?? null);
        setVisible(false);
      }
    });
  }
  async function copy(value: string) {
    await perform(async () => {
      const current = generation.current;
      await Clipboard.setStringAsync(value);
      copied.current = value;
      clipboardExpiry.current = Date.now() + 30000;
      if (current !== generation.current) {
        await clearClipboard();
        return;
      }
      if (clipboardTimer.current) clearTimeout(clipboardTimer.current);
      clipboardTimer.current = setTimeout(() => {
        void clearClipboard();
      }, 30000);
      setNotice(
        "Copiado. El portapapeles se limpiará en 30 segundos mientras la app esté activa.",
      );
    });
  }
  function generate() {
    return generatePassword(length, symbols, (size: number) =>
      Crypto.getRandomBytes(size),
    );
  }
  async function submit() {
    if (!vault || !draft) return;
    if (!draft.title.trim() || !draft.username.trim() || !draft.password) {
      setNotice("Completa el nombre, usuario y contraseña.");
      return;
    }
    let website: string;
    try {
      website = normalizeWebsite(draft.website);
    } catch {
      setNotice("Introduce una web HTTPS válida, por ejemplo ejemplo.com.");
      return;
    }
    const entry = {
      ...draft,
      website,
      title: draft.title.trim(),
      username: draft.username.trim(),
      id: draft.id || Crypto.randomUUID(),
      updatedAt: new Date().toISOString(),
    };
    const credentials = [
      ...vault.credentials.filter((item) => item.id !== entry.id),
      entry,
    ];
    await persist({ ...vault, credentials }, () => {
      setDraft(null);
      setPage("vault");
      setProfile(entry.profile);
    });
  }
  function remove(entry: Credential) {
    Alert.alert(
      "Eliminar cuenta",
      `¿Eliminar ${entry.title}? Esta acción no se puede deshacer.`,
      [
        { text: "Cancelar", style: "cancel" },
        {
          text: "Eliminar",
          style: "destructive",
          onPress: () => {
            if (vault)
              void persist(
                {
                  ...vault,
                  credentials: vault.credentials.filter(
                    (item) => item.id !== entry.id,
                  ),
                },
                () => setDetail(null),
              );
          },
        },
      ],
    );
  }
  const credentials =
    vault?.credentials
      .filter(
        (item) =>
          (profile === "Todos" || item.profile === profile) &&
          `${item.title} ${item.website}`
            .toLowerCase()
            .includes(search.toLowerCase()),
      )
      .sort((a, b) => a.title.localeCompare(b.title)) ?? [];
  const updateDraft = (field: keyof Credential, value: string) =>
    setDraft((current) => (current ? { ...current, [field]: value } : null));
  return (
    <SafeAreaView style={s.safe} onTouchStart={touch}>
      {<CaptureProtection />}
      <StatusBar barStyle="light-content" backgroundColor={colors.bg} />
      <KeyboardAvoidingView
        style={s.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <View style={s.header}>
          <View style={s.row}>
            <View style={s.logo}>
              <Text style={s.logoText}>C</Text>
            </View>
            <Text style={s.brand}>
              clave<Text style={{ color: colors.accent }}>.</Text>
            </Text>
          </View>
          {vault && (
            <Pressable accessibilityRole="button" onPress={() => lock()}>
              <Text style={s.link}>Bloquear ↗</Text>
            </Pressable>
          )}
        </View>
        <ScrollView
          contentContainerStyle={s.content}
          keyboardShouldPersistTaps="handled"
        >
          {!vault ? (
            <View style={s.welcome}>
              <Text style={s.eyebrow}>GESTOR DE CONTRASEÑAS</Text>
              <Text style={s.hero}>Tus cuentas.{"\n"}Bajo control.</Text>
              <Text style={s.description}>
                {
                  "Tus cuentas, organizadas. Tus contraseñas, protegidas con tu huella o Face ID."
                }
              </Text>
              <View style={s.card}>
                <Text style={s.cardTitle}>Una bóveda. Todos tus perfiles.</Text>
                <Text style={s.description}>
                  Ocio, trabajo y personal, en un lugar privado dentro de tu
                  dispositivo.
                </Text>
                <View style={s.badge}>
                  <Text style={s.badgeText}>
                    {"● Almacenamiento local cifrado"}
                  </Text>
                </View>
              </View>
              {!exists && (
                <Text style={s.small}>
                  Esta primera versión no tiene recuperación ni sincronización.
                  Cambiar las huellas o Face ID, perder el dispositivo o
                  desinstalar la app puede dejar tus datos inaccesibles. Prueba
                  primero con cuentas de ejemplo.
                </Text>
              )}
              <Button
                disabled={busy || exists === null}
                label={
                  busy
                    ? "Autenticando…"
                    : exists === null
                      ? "Cargando…"
                      : exists
                        ? "Desbloquear con biometría"
                        : "Crear mi bóveda"
                }
                onPress={() => void unlock()}
              />
              <Text style={s.small}>
                {"Requiere una compilación nativa y biometría configurada."}
              </Text>
            </View>
          ) : draft ? (
            <>
              <Text style={s.eyebrow}>GUARDAR EN CLAVE</Text>
              <Text style={s.title}>
                {draft.id ? "Editar cuenta" : "Nueva cuenta"}
              </Text>
              <Field
                label="Nombre de la cuenta"
                value={draft.title}
                onChangeText={(v) => updateDraft("title", v)}
                placeholder="Netflix, GitHub…"
                maxLength={100}
              />
              <Field
                label="Sitio web (opcional)"
                value={draft.website}
                onChangeText={(v) => updateDraft("website", v)}
                placeholder="https://ejemplo.com"
                keyboardType="url"
                maxLength={500}
              />
              <Field
                label="Usuario o correo"
                value={draft.username}
                onChangeText={(v) => updateDraft("username", v)}
                placeholder="tu@email.com"
                maxLength={250}
              />
              <Field
                label="Contraseña"
                value={draft.password}
                onChangeText={(v) => updateDraft("password", v)}
                secret
                maxLength={1024}
              />
              <Button
                secondary
                label="↻  Autogenerar contraseña"
                onPress={() => updateDraft("password", generate())}
                disabled={busy}
              />
              <Text style={s.label}>Guardar en el perfil</Text>
              <View style={s.chips}>
                {vault.profiles.map((p) => (
                  <Pressable
                    key={p}
                    onPress={() => updateDraft("profile", p)}
                    style={[s.chip, draft.profile === p && s.chipActive]}
                  >
                    <Text
                      style={
                        draft.profile === p ? s.chipActiveText : s.chipText
                      }
                    >
                      {p}
                    </Text>
                  </Pressable>
                ))}
              </View>
              <Button
                label={busy ? "Guardando…" : "Guardar con biometría"}
                disabled={busy}
                onPress={() => void submit()}
              />
              <Button
                secondary
                label="Cancelar"
                disabled={busy}
                onPress={() => setDraft(null)}
              />
            </>
          ) : detail ? (
            <>
              <Text style={s.eyebrow}>
                {detail.profile.toUpperCase()} · CUENTA PROTEGIDA
              </Text>
              <Text style={s.title}>{detail.title}</Text>
              <Text style={s.description}>
                {detail.website || "Sin sitio web"}
              </Text>
              {detail.status === "pending" && (
                <>
                  <Text style={s.notice}>
                    Generada y guardada. Confirma el cambio en la web; guardar
                    en Clave no cambia la contraseña del sitio.
                  </Text>
                  <Button
                    secondary
                    disabled={busy}
                    label="Ya confirmé el cambio en la web"
                    onPress={() => {
                      const updated: Credential = {
                        ...detail,
                        status: "saved",
                        previousPassword: undefined,
                      };
                      void persist(
                        {
                          ...vault,
                          credentials: vault.credentials.map((c) =>
                            c.id === updated.id ? updated : c,
                          ),
                        },
                        () => setDetail(updated),
                      );
                    }}
                  />
                  {!!detail.previousPassword && (
                    <Button
                      secondary
                      disabled={busy}
                      label="El cambio falló: restaurar contraseña anterior"
                      onPress={() => {
                        const updated: Credential = {
                          ...detail,
                          password: detail.previousPassword!,
                          previousPassword: undefined,
                          status: "saved",
                        };
                        void persist(
                          {
                            ...vault,
                            credentials: vault.credentials.map((c) =>
                              c.id === updated.id ? updated : c,
                            ),
                          },
                          () => setDetail(updated),
                        );
                      }}
                    />
                  )}
                </>
              )}
              <View style={s.card}>
                <Text style={s.label}>USUARIO</Text>
                <Text selectable style={s.secret}>
                  {detail.username}
                </Text>
                <Button
                  secondary
                  label="Copiar usuario"
                  disabled={busy}
                  onPress={() => void copy(detail.username)}
                />
                <Text style={s.label}>CONTRASEÑA</Text>
                <Text selectable={visible} style={s.secret}>
                  {visible ? detail.password : "••••••••••••••••"}
                </Text>
                <View style={s.row}>
                  <Button
                    secondary
                    label={visible ? "Ocultar" : "Mostrar"}
                    onPress={() => setVisible(!visible)}
                  />
                  <Button
                    secondary
                    label="Copiar"
                    disabled={busy}
                    onPress={() => void copy(detail.password)}
                  />
                </View>
              </View>
              <Text style={s.small}>
                Esta vista se cierra automáticamente en 30 segundos.
              </Text>
              {detail.website !== "" && (
                <Button
                  label="Abrir sitio web ↗"
                  onPress={() =>
                    void perform(async () => {
                      const url = normalizeWebsite(detail.website);
                      await Linking.openURL(url);
                    })
                  }
                />
              )}
              <Button
                secondary
                label="Editar cuenta"
                onPress={() => {
                  setDraft(detail);
                  setDetail(null);
                }}
              />
              <Button
                secondary
                label="Volver a mi bóveda"
                onPress={() => {
                  setDetail(null);
                  setVisible(false);
                }}
              />
              <Pressable disabled={busy} onPress={() => remove(detail)}>
                <Text style={s.danger}>Eliminar cuenta</Text>
              </Pressable>
            </>
          ) : page === "vault" ? (
            <>
              <Text style={s.eyebrow}>TU ESPACIO PRIVADO</Text>
              <Text style={s.title}>Mi bóveda</Text>
              <Text style={s.description}>
                {vault.credentials.length} cuentas guardadas. Un poco más de
                tranquilidad.
              </Text>
              <Field
                label="Buscar cuentas"
                value={search}
                onChangeText={setSearch}
                placeholder="Busca por nombre o sitio web…"
              />
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View style={s.chips}>
                  {["Todos", ...vault.profiles].map((p) => (
                    <Pressable
                      key={p}
                      onPress={() => setProfile(p)}
                      style={[s.chip, profile === p && s.chipActive]}
                    >
                      <Text
                        style={profile === p ? s.chipActiveText : s.chipText}
                      >
                        {p}
                      </Text>
                    </Pressable>
                  ))}
                </View>
              </ScrollView>
              {credentials.length === 0 ? (
                <View style={s.card}>
                  <Text style={s.cardTitle}>
                    {search
                      ? "No hay coincidencias"
                      : "Tu próxima contraseña empieza aquí."}
                  </Text>
                  <Text style={s.description}>
                    {search
                      ? "Prueba otro nombre o perfil."
                      : "Añade una cuenta, genera una contraseña y guárdala en el perfil que prefieras."}
                  </Text>
                </View>
              ) : (
                credentials.map((entry) => (
                  <Pressable
                    disabled={busy}
                    accessibilityRole="button"
                    accessibilityLabel={`Abrir ${entry.title}${" con biometría"}`}
                    key={entry.id}
                    style={s.entry}
                    onPress={() => void viewEntry(entry.id)}
                  >
                    <View style={s.avatar}>
                      <Text style={s.avatarText}>
                        {entry.title.slice(0, 1).toUpperCase()}
                      </Text>
                    </View>
                    <View style={s.flex}>
                      <Text style={s.cardTitle}>{entry.title}</Text>
                      <Text style={s.small}>
                        {entry.profile} ·{" "}
                        {entry.website.replace("https://", "") ||
                          "Cuenta privada"}
                      </Text>
                    </View>
                    <Text style={s.link}>→</Text>
                  </Pressable>
                ))
              )}
              <Button
                label="+  Añadir cuenta"
                disabled={busy}
                onPress={() => {
                  setNotice("");
                  setDraft(
                    emptyEntry(
                      profile === "Todos" ? vault.profiles[0] : profile,
                    ),
                  );
                }}
              />
              <View style={s.tip}>
                <Text style={s.label}>¿Has olvidado una contraseña?</Text>
                <Text style={s.small}>
                  Inicia la recuperación en la web. En el campo de nueva
                  contraseña, selecciona Clave para generar y guardar en tu
                  perfil. Confirma después el cambio en la web.
                </Text>
              </View>
            </>
          ) : page === "generator" ? (
            <>
              <Text style={s.eyebrow}>CONTRASEÑAS ÚNICAS</Text>
              <Text style={s.title}>Generador</Text>
              <Text style={s.description}>
                Crea una contraseña diferente para cada cuenta.
              </Text>
              <View style={s.card}>
                <Text selectable style={s.generated}>
                  {generated || "Tu nueva contraseña aparecerá aquí"}
                </Text>
              </View>
              <View style={s.setting}>
                <Text style={s.label}>Longitud</Text>
                <View style={s.row}>
                  <Button
                    secondary
                    label="−"
                    disabled={length <= 12}
                    onPress={() => {
                      setLength(length - 4);
                      setGenerated("");
                    }}
                  />
                  <Text style={s.cardTitle}>{length}</Text>
                  <Button
                    secondary
                    label="+"
                    disabled={length >= 64}
                    onPress={() => {
                      setLength(length + 4);
                      setGenerated("");
                    }}
                  />
                </View>
              </View>
              <View style={s.setting}>
                <Text style={s.label}>Incluir símbolos</Text>
                <Switch
                  accessibilityLabel="Incluir símbolos"
                  value={symbols}
                  onValueChange={(v) => {
                    setSymbols(v);
                    setGenerated("");
                  }}
                  trackColor={{ true: colors.accent }}
                />
              </View>
              <Button
                label="↻  Generar contraseña"
                onPress={() => setGenerated(generate())}
              />
              {!!generated && (
                <>
                  <Button
                    secondary
                    label="Copiar contraseña"
                    disabled={busy}
                    onPress={() => void copy(generated)}
                  />
                  <Button
                    secondary
                    label="Usar en una nueva cuenta"
                    onPress={() => {
                      setDraft({
                        ...emptyEntry(vault.profiles[0]),
                        password: generated,
                      });
                      setGenerated("");
                    }}
                  />
                </>
              )}
            </>
          ) : (
            <>
              <Text style={s.eyebrow}>A TU MANERA</Text>
              <Text style={s.title}>Perfiles y seguridad</Text>
              <AutofillSettings />
              <Text style={s.description}>
                Separa tus cuentas según su propósito.
              </Text>
              <View style={s.card}>
                {vault.profiles.map((p) => (
                  <View key={p} style={s.setting}>
                    <Text style={s.cardTitle}>{p}</Text>
                    <Text style={s.small}>
                      {vault.credentials.filter((c) => c.profile === p).length}{" "}
                      cuentas
                    </Text>
                  </View>
                ))}
              </View>
              <Field
                label="Nuevo perfil"
                value={newProfile}
                onChangeText={setNewProfile}
                placeholder="Estudios, familia…"
                maxLength={30}
              />
              <Button
                secondary
                label="Crear perfil"
                disabled={busy}
                onPress={() => {
                  const name = newProfile.trim();
                  if (
                    !name ||
                    name.toLowerCase() === "todos" ||
                    vault.profiles.some(
                      (p) => p.toLowerCase() === name.toLowerCase(),
                    )
                  ) {
                    setNotice("Elige un nombre de perfil nuevo.");
                    return;
                  }
                  void persist(
                    { ...vault, profiles: [...vault.profiles, name] },
                    () => setNewProfile(""),
                  );
                }}
              />
              <View style={s.tip}>
                <Text style={s.cardTitle}>Seguridad de esta versión</Text>
                <Text style={s.description}>
                  {
                    "Bóveda cifrada en este dispositivo. Biometría al abrir y guardar. Bloqueo al pasar a segundo plano o tras 2 minutos sin actividad."
                  }
                </Text>
                <Text style={s.small}>
                  {
                    "Autocompletado disponible en Android mediante Clave. Sin recuperación ni sincronización todavía. No cambies la biometría ni desinstales la app si necesitas conservar estos datos."
                  }
                </Text>
              </View>
              <Button label="Bloquear ahora" onPress={lock} />
            </>
          )}
          {!!notice && (
            <Text accessibilityLiveRegion="polite" style={s.notice}>
              {notice}
            </Text>
          )}
          {busy && vault && (
            <Text accessibilityLiveRegion="polite" style={s.small}>
              {"Verificando con el dispositivo…"}
            </Text>
          )}
        </ScrollView>
        {!!vault && !draft && !detail && (
          <View style={s.nav}>
            {(
              [
                ["vault", "◈", "Bóveda"],
                ["generator", "✳", "Generador"],
                ["settings", "◎", "Perfiles"],
              ] as const
            ).map(([id, icon, label]) => (
              <Pressable
                accessibilityRole="tab"
                accessibilityState={{ selected: page === id }}
                key={id}
                onPress={() => {
                  setPage(id);
                  setNotice("");
                  setGenerated("");
                }}
                style={s.navItem}
              >
                <Text style={[s.navIcon, page === id && s.active]}>{icon}</Text>
                <Text style={[s.small, page === id && s.active]}>{label}</Text>
              </Pressable>
            ))}
          </View>
        )}
      </KeyboardAvoidingView>
      {covered && (
        <View style={s.cover}>
          <Text style={s.brand}>clave.</Text>
          <Text style={s.description}>{"Tu bóveda está protegida"}</Text>
        </View>
      )}
    </SafeAreaView>
  );
}
export default function App() {
  if (isExpoGo)
    return (
      <SafeAreaProvider>
        <SafeAreaView
          style={[s.safe, { padding: 28, justifyContent: "center", gap: 20 }]}
        >
          <Text style={s.title}>Instala Clave</Text>
          <Text style={s.description}>
            Esta aplicación necesita su propia instalación para proteger la
            bóveda y autocompletar contraseñas. Expo Go no es compatible.
          </Text>
          <Text style={s.small}>
            Android: instala el APK de Clave. iPhone: utiliza una compilación
            firmada de la app.
          </Text>
        </SafeAreaView>
      </SafeAreaProvider>
    );
  return (
    <SafeAreaProvider>
      <VaultApp />
    </SafeAreaProvider>
  );
}
const s = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  flex: { flex: 1 },
  header: {
    paddingHorizontal: 24,
    paddingVertical: 18,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  row: { flexDirection: "row", alignItems: "center", gap: 12 },
  logo: {
    width: 34,
    height: 34,
    borderRadius: 12,
    backgroundColor: colors.accent,
    alignItems: "center",
    justifyContent: "center",
  },
  logoText: { fontWeight: "900", fontSize: 24, color: colors.bg },
  brand: {
    fontSize: 29,
    fontWeight: "800",
    color: colors.text,
    letterSpacing: -1,
  },
  content: {
    padding: 24,
    paddingTop: 18,
    gap: 18,
    width: "100%",
    maxWidth: 660,
    alignSelf: "center",
    paddingBottom: 40,
  },
  welcome: { gap: 24, paddingTop: 30 },
  eyebrow: {
    fontSize: 11,
    letterSpacing: 2,
    fontWeight: "700",
    color: colors.accent,
  },
  hero: {
    fontSize: 47,
    lineHeight: 53,
    fontWeight: "700",
    letterSpacing: -2,
    color: colors.text,
  },
  title: {
    fontSize: 34,
    fontWeight: "700",
    letterSpacing: -1,
    color: colors.text,
  },
  description: { fontSize: 16, lineHeight: 25, color: colors.muted },
  small: { fontSize: 12, lineHeight: 19, color: colors.muted },
  card: {
    padding: 23,
    gap: 18,
    backgroundColor: colors.panel,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 22,
  },
  cardTitle: { color: colors.text, fontSize: 17, fontWeight: "600" },
  badge: {
    alignSelf: "flex-start",
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 20,
    backgroundColor: "#292D35",
  },
  badgeText: { color: colors.accent, fontSize: 11 },
  button: {
    backgroundColor: colors.accent,
    paddingVertical: 16,
    paddingHorizontal: 20,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  secondary: {
    backgroundColor: colors.panel,
    borderWidth: 1,
    borderColor: colors.line,
  },
  buttonText: { color: colors.bg, fontWeight: "700", fontSize: 15 },
  link: { color: colors.accent, fontSize: 14, fontWeight: "600" },
  field: { gap: 9 },
  label: { color: colors.muted, fontSize: 13, fontWeight: "600" },
  input: {
    color: colors.text,
    fontSize: 16,
    backgroundColor: colors.panel,
    borderColor: colors.line,
    borderWidth: 1,
    borderRadius: 14,
    padding: 16,
  },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: 8, paddingVertical: 4 },
  chip: {
    paddingVertical: 11,
    paddingHorizontal: 17,
    backgroundColor: colors.panel,
    borderRadius: 24,
  },
  chipActive: { backgroundColor: colors.accent },
  chipText: { color: colors.muted, fontSize: 13 },
  chipActiveText: { color: colors.bg, fontWeight: "600", fontSize: 13 },
  entry: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    padding: 17,
    backgroundColor: colors.panel,
    borderRadius: 18,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 14,
    backgroundColor: "#292D35",
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: { color: colors.accent, fontSize: 22, fontWeight: "700" },
  tip: { padding: 18, gap: 12, borderLeftWidth: 2, borderColor: colors.accent },
  secret: {
    color: colors.text,
    fontSize: 21,
    fontFamily: Platform.OS === "ios" ? "Menlo" : "monospace",
  },
  generated: {
    color: colors.accent,
    fontSize: 26,
    lineHeight: 38,
    fontFamily: Platform.OS === "ios" ? "Menlo" : "monospace",
  },
  setting: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 10,
  },
  danger: { color: "#FFB0A6", textAlign: "center", padding: 14 },
  notice: {
    color: colors.accent,
    lineHeight: 22,
    padding: 14,
    borderRadius: 12,
    backgroundColor: colors.panel,
  },
  nav: {
    flexDirection: "row",
    borderTopWidth: 1,
    borderColor: colors.line,
    paddingVertical: 14,
  },
  navItem: { flex: 1, alignItems: "center", gap: 4 },
  navIcon: { color: colors.muted, fontSize: 24 },
  active: { color: colors.accent },
  cover: {
    position: "absolute",
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    backgroundColor: colors.bg,
    alignItems: "center",
    justifyContent: "center",
    gap: 20,
  },
});
