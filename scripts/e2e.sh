#!/usr/bin/env bash
# Tests de bout en bout sur émulateur : installe Zen Écran + fausses applis, active le service,
# applique des configurations et vérifie que les blocages se déclenchent vraiment.
set -u
OUT="$PWD/e2e-out"
mkdir -p "$OUT"
R="$OUT/report.md"
echo "# Résultats des tests émulateur" > "$R"
PASS=0
FAIL=0
ZEN=com.zenecran
SVC=com.zenecran/com.zenecran.service.GuardService
IG=com.instagram.android
FB=com.facebook.katana

log() { echo "$*" | tee -a "$R"; }
ok() { PASS=$((PASS + 1)); log "- ✅ $*"; }
ko() { FAIL=$((FAIL + 1)); log "- ❌ $*"; }
section() { log ""; log "## $*"; }

save_logs() {
    adb logcat -d -s ZenEcran:V AndroidRuntime:E >> "$OUT/logcat.txt" 2>/dev/null
    adb logcat -d -b crash >> "$OUT/crash.txt" 2>/dev/null
    adb logcat -c
}
has_log() { adb logcat -d -s ZenEcran:I | grep -q -- "$1"; }
wait_log() { # motif, délai (s)
    for _ in $(seq 1 "$2"); do has_log "$1" && return 0; sleep 1; done
    return 1
}
top() { adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
stop_fakes() { adb shell am force-stop $IG; adb shell am force-stop $FB; }

svc_off() {
    adb shell settings delete secure enabled_accessibility_services > /dev/null
    adb shell am force-stop $ZEN
    sleep 1
}
svc_on() {
    adb shell settings put secure enabled_accessibility_services $SVC
    adb shell settings put secure accessibility_enabled 1
    if wait_log SERVICE_CONNECTED 25; then sleep 2; return 0; fi
    ko "le service ne s'est pas connecté"
    return 1
}
set_config() { # JSON de configuration
    save_logs
    svc_off
    stop_fakes
    printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <string name="config">%s</string>\n</map>\n' "$1" > /tmp/zen.xml
    adb push /tmp/zen.xml /data/local/tmp/zen.xml > /dev/null
    adb shell "cat /data/local/tmp/zen.xml | run-as $ZEN sh -c 'mkdir -p shared_prefs && rm -f shared_prefs/zen.xml.bak && cat > shared_prefs/zen.xml'"
    adb shell input keyevent KEYCODE_HOME
    svc_on
}
start() { adb shell am start -W -n "$1" > /dev/null; }

# ------------------------------------------------------------------ installation
adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard 2>/dev/null
adb shell svc power stayon true
adb install -r app/build/outputs/apk/debug/app-debug.apk | tail -1
adb install -r fake-instagram/build/outputs/apk/debug/fake-instagram-debug.apk | tail -1
adb install -r fake-facebook/build/outputs/apk/debug/fake-facebook-debug.apk | tail -1
adb shell appops set $ZEN GET_USAGE_STATS allow
adb shell dumpsys deviceidle whitelist +$ZEN > /dev/null
adb shell settings put system screen_off_timeout 1800000

# Échauffement : laisser Android finir son premier démarrage (optimisation des applis, etc.)
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
for a in $IG/.MainActivity $FB/.MainActivity $ZEN/.MainActivity; do adb shell am start -W -n $a > /dev/null; sleep 3; done
adb shell input keyevent KEYCODE_HOME
adb shell cmd package bg-dexopt-job > /dev/null 2>&1
sleep 30
adb logcat -c

# ------------------------------------------------------------------ 1. limite quotidienne
section "Limite quotidienne (1 min sur Faux Instagram)"
set_config '{"limits":{"com.instagram.android":1},"warn":0}'
T0=$(date +%s)
start $IG/.MainActivity
if wait_log "BLOCK_APP pkg=$IG reason=limit" 110; then
    DT=$(( $(date +%s) - T0 ))
    if [ "$DT" -ge 45 ]; then ok "bloqué après ${DT} s d'utilisation"; else ko "bloqué trop tôt (${DT} s) : temps d'écran mal compté"; fi
    sleep 2; shot 01_limite
    top | grep -q $IG && ko "Faux Instagram encore au premier plan" || ok "retour à l'accueil"
else
    ko "jamais bloqué après 110 s"
    shot 01_echec_limite
fi
adb logcat -d -s ZenEcran:V | grep -E " FG |WATCHDOG|UNBIND" | tail -8 | sed 's/^/    /' >> "$R"
echo "    services d'accessibilité activés : $(adb shell settings get secure enabled_accessibility_services | tr -d '\r')" >> "$R"
adb logcat -d -s ZenEcran:I | grep USAGE | tail -3 | sed 's/^/    /' >> "$R"

# ------------------------------------------------------------------ 2. horaire
section "Plage horaire (toute la journée)"
set_config '{"schedules":[{"id":"t","name":"Test","pkgs":["com.instagram.android"],"days":[1,2,3,4,5,6,7],"start":0,"end":0,"on":true}]}'
start $IG/.MainActivity
if wait_log "BLOCK_APP pkg=$IG reason=schedule" 10; then ok "bloqué par la plage horaire"; else ko "pas bloqué par la plage horaire"; fi
sleep 2; shot 02_horaire
top | grep -q $IG && ko "Faux Instagram encore au premier plan" || ok "retour à l'accueil"
adb shell dumpsys window windows | grep -q "zenecran" && ok "écran de blocage affiché" || ko "écran de blocage absent"

# plage qui ne couvre pas maintenant : pas de blocage
HNOW=$(adb shell date +%H | tr -d '\r' | sed 's/^0//')
H=$(( ( ${HNOW:-0} + 3 ) % 24 ))
set_config "{\"schedules\":[{\"id\":\"t\",\"name\":\"Plus tard\",\"pkgs\":[\"com.instagram.android\"],\"days\":[1,2,3,4,5,6,7],\"start\":$((H*60)),\"end\":$((H*60+30)),\"on\":true}]}"
start $IG/.MainActivity
sleep 5
has_log "BLOCK_APP" && ko "bloqué alors que la plage n'est pas active" || ok "pas de blocage hors de la plage"

# ------------------------------------------------------------------ 3. concentration
section "Mode concentration"
NOW=$(( $(date +%s) * 1000 ))
set_config "{\"limits\":{\"com.facebook.katana\":0},\"focus\":$((NOW + 3600000))}"
start $FB/.MainActivity
if wait_log "BLOCK_APP pkg=$FB reason=focus" 10; then ok "Faux Facebook bloqué pendant la concentration"; else ko "pas bloqué pendant la concentration"; fi
sleep 1; shot 03_concentration

# ------------------------------------------------------------------ 4. contenus
feature_test() { # règle, activité, nom
    set_config "{\"rules\":{\"$1\":{\"e\":true,\"b\":0}},\"diag\":true}"
    local pkg=${2%%/*}
    start "$pkg/.MainActivity"
    sleep 2
    start "$2"
    if wait_log "BLOCK_FEATURE id=$1" 10; then
        ok "$3 détecté et bloqué"
        sleep 2
        top | grep -q "$2" && ko "$3 encore ouvert après le blocage" || ok "$3 fermé"
    else
        ko "$3 NON détecté"
        shot "echec_$1"
        echo "    ce que voit le service :" >> "$R"
        adb logcat -d -s ZenEcran:I | grep " DUMP " | tail -40 | sed 's/.* DUMP /    /' >> "$R"
    fi
}
section "Contenus (Reels, Stories)"
feature_test ig_reels "$IG/.ReelsActivity" "Reels Instagram"
feature_test ig_stories "$IG/.StoryActivity" "Stories Instagram"
feature_test fb_stories "$FB/.StoryViewerActivity" "Stories Facebook"
feature_test fb_reels "$FB/.ReelsTabActivity" "Onglet Reels Facebook"
feature_test fb_reels "$FB/.ReelsViewerActivity" "Lecteur Reels Facebook (structure)"

section "Pas de faux positif"
set_config '{"rules":{"ig_reels":{"e":true,"b":0},"ig_stories":{"e":true,"b":0},"fb_reels":{"e":true,"b":0},"fb_stories":{"e":true,"b":0},"fb_video":{"e":true,"b":0}}}'
start $FB/.MainActivity
sleep 6
start $IG/.MainActivity
sleep 6
has_log "BLOCK_FEATURE" && ko "blocage déclenché sur un écran autorisé : $(adb logcat -d -s ZenEcran:I | grep BLOCK_FEATURE | head -1)" || ok "les accueils Instagram et Facebook restent utilisables"

section "Budget (1 min de Reels par jour)"
set_config '{"rules":{"ig_reels":{"e":true,"b":1}}}'
start $IG/.MainActivity
sleep 1
T0=$(date +%s)
start $IG/.ReelsActivity
if wait_log "BLOCK_FEATURE id=ig_reels" 100; then
    DT=$(( $(date +%s) - T0 ))
    if [ "$DT" -ge 45 ]; then ok "Reels bloqués après ${DT} s (budget respecté)"; else ko "Reels bloqués trop tôt (${DT} s)"; fi
else
    ko "budget jamais épuisé"
fi

# ------------------------------------------------------------------ 5. interface
section "Interface (captures)"
set_config '{"limits":{"com.instagram.android":30,"com.facebook.katana":45},"rules":{"ig_reels":{"e":true,"b":0},"fb_stories":{"e":true,"b":10}},"schedules":[{"id":"n","name":"Nuit","pkgs":["com.instagram.android","com.facebook.katana"],"days":[1,2,3,4,5],"start":1320,"end":420,"on":true}]}'
adb shell am start -W -n $ZEN/.MainActivity > /dev/null
sleep 4
shot 10_accueil
for tab in Limites Horaires Contenu Réglages; do
    if python3 scripts/tap.py "$tab"; then sleep 2; shot "11_$tab"; else ko "onglet $tab introuvable"; fi
done
python3 scripts/tap.py "Horaires" && sleep 1 && python3 scripts/tap.py "Nuit" && sleep 2 && shot 12_editeur_horaire
adb shell input keyevent KEYCODE_BACK
top | grep -q $ZEN && ok "l'interface s'ouvre et navigue sans planter" || ko "l'interface n'est plus au premier plan"

# ------------------------------------------------------------------ bilan
save_logs
section "Plantages"
if grep -q "com.zenecran" "$OUT/crash.txt" 2>/dev/null; then
    ko "plantage détecté :"
    grep -A 12 "FATAL EXCEPTION" "$OUT/crash.txt" | head -40 | sed 's/^/    /' >> "$R"
else
    ok "aucun plantage de Zen Écran"
fi
log ""
log "**Total : $PASS réussis, $FAIL échoués**"
[ "$FAIL" -eq 0 ]
