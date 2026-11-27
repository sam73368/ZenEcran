package com.zenecran.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Persistance locale (SharedPreferences + JSON). Partagée entre l'interface et le service. */
object Store {
    private var prefs: SharedPreferences? = null

    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config.asStateFlow()

    private val _daily = MutableStateFlow(Daily(day = today()))
    val daily: StateFlow<Daily> = _daily.asStateFlow()

    /** Dernière capture du Mode diagnostic, par appli. */
    private val _dumps = MutableStateFlow<Map<String, String>>(emptyMap())
    val dumps: StateFlow<Map<String, String>> = _dumps.asStateFlow()

    /** Dernier « battement » du service (SystemClock.elapsedRealtime), pour savoir s'il tourne vraiment. */
    private val _beat = MutableStateFlow(0L)
    val beat: StateFlow<Long> = _beat.asStateFlow()

    fun beat() {
        _beat.value = android.os.SystemClock.elapsedRealtime()
    }

    fun today(): String = LocalDate.now().toString()

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("zen", Context.MODE_PRIVATE)
        prefs = p
        _config.value = decodeConfig(p.getString("config", null))
        _daily.value = decodeDaily(p.getString("daily", null))
        rollover()
    }

    fun updateConfig(f: (Config) -> Config) {
        _config.value = f(_config.value)
        prefs?.edit()?.putString("config", encodeConfig(_config.value))?.apply()
    }

    fun daily(): Daily {
        rollover()
        return _daily.value
    }

    fun updateDaily(persist: Boolean = true, f: (Daily) -> Daily) {
        rollover()
        _daily.value = f(_daily.value)
        if (persist) flushDaily()
    }

    fun flushDaily() {
        prefs?.edit()?.putString("daily", encodeDaily(_daily.value))?.apply()
    }

    fun setDump(pkg: String, s: String) {
        _dumps.value = _dumps.value + (pkg to s)
    }

    fun clearDumps() {
        _dumps.value = emptyMap()
    }

    private fun rollover() {
        if (_daily.value.day != today()) _daily.value = Daily(day = today())
    }

    // ---------- JSON ----------

    private fun encodeConfig(c: Config): String {
        val o = JSONObject()
        val limits = JSONObject()
        c.limits.forEach { (k, v) -> limits.put(k, v) }
        o.put("limits", limits)
        val rules = JSONObject()
        c.rules.forEach { (k, v) -> rules.put(k, JSONObject().put("e", v.enabled).put("b", v.budgetMin)) }
        o.put("rules", rules)
        val schedules = JSONArray()
        c.schedules.forEach { s ->
            schedules.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("pkgs", JSONArray(s.pkgs.toList()))
                    .put("days", JSONArray(s.days.toList()))
                    .put("start", s.startMin)
                    .put("end", s.endMin)
                    .put("on", s.enabled)
            )
        }
        o.put("schedules", schedules)
        val custom = JSONArray()
        c.customRules.forEach { r ->
            custom.put(
                JSONObject()
                    .put("id", r.id)
                    .put("pkg", r.pkg)
                    .put("title", r.title)
                    .put("labels", JSONArray(r.selectedLabels))
                    .put("ids", JSONArray(r.ids))
                    .put("acts", JSONArray(r.activities))
            )
        }
        o.put("custom", custom)
        o.put("goal", c.goalMin)
        o.put("focus", c.focusUntil)
        o.put("strict", c.strictUntil)
        o.put("diag", c.diagnostic)
        o.put("accent", c.accent)
        o.put("amoled", c.amoled)
        o.put("quote", c.quote)
        o.put("extMin", c.extMinutes)
        o.put("extMax", c.extMax)
        o.put("extWait", c.extWaitSec)
        o.put("warn", c.warnMin)
        o.put("fBack", c.featureBack)
        return o.toString()
    }

    private fun decodeConfig(s: String?): Config {
        if (s == null) return Config()
        return try {
            val o = JSONObject(s)
            val limits = mutableMapOf<String, Int>()
            o.optJSONObject("limits")?.let { l -> l.keys().forEach { k -> limits[k] = l.optInt(k) } }
            val rules = mutableMapOf<String, RuleConfig>()
            o.optJSONObject("rules")?.let { r ->
                r.keys().forEach { k ->
                    val x = r.getJSONObject(k)
                    rules[k] = RuleConfig(x.optBoolean("e"), x.optInt("b"))
                }
            }
            val schedules = mutableListOf<Schedule>()
            o.optJSONArray("schedules")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val x = arr.getJSONObject(i)
                    val pk = mutableSetOf<String>()
                    x.optJSONArray("pkgs")?.let { a -> for (j in 0 until a.length()) pk += a.getString(j) }
                    val dy = mutableSetOf<Int>()
                    x.optJSONArray("days")?.let { a -> for (j in 0 until a.length()) dy += a.getInt(j) }
                    schedules += Schedule(
                        id = x.getString("id"),
                        name = x.optString("name", "Plage"),
                        pkgs = pk,
                        days = dy,
                        startMin = x.optInt("start", 22 * 60),
                        endMin = x.optInt("end", 7 * 60),
                        enabled = x.optBoolean("on", true),
                    )
                }
            }
            val custom = mutableListOf<CustomRule>()
            o.optJSONArray("custom")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val x = arr.getJSONObject(i)
                    val lb = mutableListOf<String>()
                    x.optJSONArray("labels")?.let { a -> for (j in 0 until a.length()) lb += a.getString(j) }
                    val ids = mutableListOf<String>()
                    x.optJSONArray("ids")?.let { a -> for (j in 0 until a.length()) ids += a.getString(j) }
                    val acts = mutableListOf<String>()
                    x.optJSONArray("acts")?.let { a -> for (j in 0 until a.length()) acts += a.getString(j) }
                    custom += CustomRule(x.getString("id"), x.getString("pkg"), x.optString("title", "Règle"), lb, ids, acts)
                }
            }
            Config(
                limits = limits,
                rules = rules,
                customRules = custom,
                schedules = schedules,
                goalMin = o.optInt("goal", 180),
                focusUntil = o.optLong("focus", 0L),
                strictUntil = o.optLong("strict", 0L),
                diagnostic = o.optBoolean("diag", false),
                accent = o.optInt("accent", 0),
                amoled = o.optBoolean("amoled", false),
                quote = o.optString("quote", ""),
                extMinutes = o.optInt("extMin", 5),
                extMax = o.optInt("extMax", 2),
                extWaitSec = o.optInt("extWait", 15),
                warnMin = o.optInt("warn", 5),
                featureBack = o.optBoolean("fBack", true),
            )
        } catch (e: Exception) {
            Config()
        }
    }

    private fun encodeDaily(d: Daily): String {
        val o = JSONObject().put("day", d.day)
        val f = JSONObject()
        d.featureSec.forEach { (k, v) -> f.put(k, v) }
        val e = JSONObject()
        d.extras.forEach { (k, v) -> e.put(k, v) }
        val a = JSONObject()
        d.appMs.forEach { (k, v) -> a.put(k, v) }
        o.put("f", f).put("e", e).put("a", a)
        return o.toString()
    }

    private fun decodeDaily(s: String?): Daily {
        if (s == null) return Daily(day = today())
        return try {
            val o = JSONObject(s)
            val f = mutableMapOf<String, Int>()
            o.optJSONObject("f")?.let { j -> j.keys().forEach { k -> f[k] = j.optInt(k) } }
            val e = mutableMapOf<String, Int>()
            o.optJSONObject("e")?.let { j -> j.keys().forEach { k -> e[k] = j.optInt(k) } }
            val a = mutableMapOf<String, Int>()
            o.optJSONObject("a")?.let { j -> j.keys().forEach { k -> a[k] = j.optInt(k) } }
            Daily(o.optString("day", today()), f, e, a)
        } catch (ex: Exception) {
            Daily(day = today())
        }
    }
}
