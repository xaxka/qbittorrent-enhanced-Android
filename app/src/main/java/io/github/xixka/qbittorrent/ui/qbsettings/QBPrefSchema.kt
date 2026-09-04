package io.github.xixka.qbittorrent.ui.qbsettings

import com.google.gson.JsonElement
import io.github.xixka.qbittorrent.R

/**
 * Schema of the qBittorrent WebUI preferences API (GET /app/preferences +
 * POST /app/setPreferences), used by the dynamic preferences editor.
 *
 * The KNOWN part of the schema below only decides presentation (section,
 * label, control kind, range, unit, enum labels): the editor itself renders
 * whatever the server returns. Keys a newer qBittorrent adds that this table
 * does not know are inferred at runtime (see [inferField]) and shown in the
 * "other" section with a generated label — new engine versions therefore
 * need no app update to stay fully editable.
 */

/** Control kind used to render one preference key. */
enum class PrefKind { BOOL, INT, LONG, FLOAT, TEXT, MULTILINE, PASSWORD, DROPDOWN }

/** Display unit conversions between the wire format and the input field. */
enum class PrefUnit { NONE, KIB_PER_SEC }

/** Line-based codecs for the two structured preference values. */
enum class PrefCodec { NONE, SCAN_DIRS, HTTP_HEADERS }

/** One option of a DROPDOWN field: wire value + localized label. */
class PrefOption(val value: Any, val label: Int)

class PrefField(
    val key: String,
    val label: Int,
    val kind: PrefKind,
    val def: Any? = null,
    val min: Long? = null,
    val max: Long? = null,
    val unit: PrefUnit = PrefUnit.NONE,
    val options: List<PrefOption> = emptyList(),
    val blankKeepsValue: Boolean = false,
    val legacyNumeric: Boolean = false,
    val codec: PrefCodec = PrefCodec.NONE,
) {
    /** Dynamic (non-resource) label for inferred unknown keys. */
    var labelText: String? = null
}

sealed class PrefEntry {
    class Header(val label: Int) : PrefEntry()
    class Row(val field: PrefField) : PrefEntry()
}

class PrefSection(val title: Int, val entries: List<PrefEntry>)

object QBPrefSchema {

    private fun header(label: Int) = PrefEntry.Header(label)

    private fun opt(value: Any, label: Int) = PrefOption(value, label)

    private fun field(
        key: String,
        label: Int,
        kind: PrefKind,
        def: Any? = null,
        min: Long? = null,
        max: Long? = null,
        unit: PrefUnit = PrefUnit.NONE,
        options: List<PrefOption> = emptyList(),
        blankKeepsValue: Boolean = false,
        legacyNumeric: Boolean = false,
        codec: PrefCodec = PrefCodec.NONE,
    ): PrefEntry = PrefEntry.Row(
        PrefField(key, label, kind, def, min, max, unit, options, blankKeepsValue, legacyNumeric, codec),
    )

    val sections: List<PrefSection> = listOf(
        PrefSection(R.string.qbt_tab_downloads, listOf(
            header(R.string.qbt_dl_when_adding),
            field("add_stopped_enabled", R.string.qbt_add_stopped, PrefKind.BOOL, def = false),
            field("add_to_top_of_queue", R.string.qbt_add_top, PrefKind.BOOL, def = false),
            field(
                "torrent_content_layout",
                R.string.qbt_content_layout,
                PrefKind.DROPDOWN,
                def = "Original",
                options = listOf(opt("Original", R.string.qbt_content_layout_original), opt("Subfolder", R.string.qbt_content_layout_subfolder), opt("NoSubfolder", R.string.qbt_content_layout_nosubfolder)),
            ),
            field(
                "torrent_stop_condition",
                R.string.qbt_stop_condition,
                PrefKind.DROPDOWN,
                def = "None",
                options = listOf(opt("None", R.string.qbt_stop_condition_none), opt("MetadataReceived", R.string.qbt_stop_condition_metadata), opt("FilesChecked", R.string.qbt_stop_condition_checked)),
            ),
            field("merge_trackers", R.string.qbt_merge_trackers, PrefKind.BOOL, def = false),
            field(
                "auto_delete_mode",
                R.string.qbt_auto_delete,
                PrefKind.DROPDOWN,
                def = 0,
                options = listOf(opt(0, R.string.qbt_auto_delete_never), opt(1, R.string.qbt_auto_delete_added), opt(2, R.string.qbt_auto_delete_downloaded)),
            ),
            header(R.string.qbt_dl_saving),
            field("save_path", R.string.qbt_save_path, PrefKind.TEXT),
            field("temp_path_enabled", R.string.qbt_temp_path_enabled, PrefKind.BOOL, def = false),
            field("temp_path", R.string.qbt_temp_path, PrefKind.TEXT),
            field("preallocate_all", R.string.qbt_preallocate, PrefKind.BOOL, def = false),
            field("incomplete_files_ext", R.string.qbt_incomplete_ext, PrefKind.BOOL, def = false),
            field("use_unwanted_folder", R.string.qbt_unwanted_folder, PrefKind.BOOL, def = false),
            header(R.string.qbt_tmm_section),
            field("auto_tmm_enabled", R.string.qbt_auto_tmm_default, PrefKind.BOOL, def = true),
            field("torrent_changed_tmm_enabled", R.string.qbt_tmm_torrent_changed, PrefKind.BOOL, def = true),
            field("save_path_changed_tmm_enabled", R.string.qbt_tmm_save_path_changed, PrefKind.BOOL, def = true),
            field("category_changed_tmm_enabled", R.string.qbt_tmm_category_changed, PrefKind.BOOL, def = true),
            field("use_category_paths_in_manual_mode", R.string.qbt_use_category_paths, PrefKind.BOOL, def = true),
            header(R.string.qbt_export_section),
            field("export_dir", R.string.qbt_export_dir, PrefKind.TEXT),
            field("export_dir_fin", R.string.qbt_export_dir_fin, PrefKind.TEXT),
            header(R.string.qbt_autorun_section),
            field("autorun_enabled", R.string.qbt_autorun_enabled, PrefKind.BOOL, def = false),
            field("autorun_program", R.string.qbt_autorun_program, PrefKind.TEXT),
            field(
                "autorun_on_torrent_added_enabled",
                R.string.qbt_autorun_on_added_enabled,
                PrefKind.BOOL,
                def = false,
            ),
            field("autorun_on_torrent_added_program", R.string.qbt_autorun_on_added_program, PrefKind.TEXT),
            header(R.string.qbt_scan_dirs_section),
            field("scan_dirs", R.string.qbt_scan_dirs, PrefKind.MULTILINE, codec = PrefCodec.SCAN_DIRS),
            header(R.string.qbt_section_deleting),
            field("mark_of_the_web", R.string.qbt_mark_of_web, PrefKind.BOOL, def = false),
            field("recheck_completed_torrents", R.string.qbt_recheck_completed, PrefKind.BOOL, def = false),
            field(
                "torrent_content_remove_option",
                R.string.qbt_content_remove_option,
                PrefKind.DROPDOWN,
                def = "MoveToTrash",
                options = listOf(opt("MoveToTrash", R.string.qbt_remove_option_trash), opt("Delete", R.string.qbt_remove_option_delete)),
            ),
            header(R.string.qbt_section_mail),
            field("mail_notification_enabled", R.string.qbt_mail_enabled, PrefKind.BOOL, def = false),
            field("mail_notification_sender", R.string.qbt_mail_sender, PrefKind.TEXT),
            field("mail_notification_email", R.string.qbt_mail_email, PrefKind.TEXT),
            field("mail_notification_smtp", R.string.qbt_mail_smtp, PrefKind.TEXT),
            field("mail_notification_ssl_enabled", R.string.qbt_mail_ssl, PrefKind.BOOL, def = false),
            field("mail_notification_auth_enabled", R.string.qbt_mail_auth, PrefKind.BOOL, def = false),
            field("mail_notification_username", R.string.qbt_mail_username, PrefKind.TEXT),
            field("mail_notification_password", R.string.qbt_mail_password, PrefKind.TEXT),
        )),
        PrefSection(R.string.qbt_tab_speed, listOf(
            header(R.string.qbt_speed_global),
            field(
                "dl_limit",
                R.string.qbt_dl_limit,
                PrefKind.INT,
                def = 0,
                unit = PrefUnit.KIB_PER_SEC,
                blankKeepsValue = true,
            ),
            field(
                "up_limit",
                R.string.qbt_up_limit,
                PrefKind.INT,
                def = 0,
                unit = PrefUnit.KIB_PER_SEC,
                blankKeepsValue = true,
            ),
            header(R.string.qbt_speed_alt),
            field(
                "alt_dl_limit",
                R.string.qbt_alt_dl_limit,
                PrefKind.INT,
                def = 0,
                unit = PrefUnit.KIB_PER_SEC,
                blankKeepsValue = true,
            ),
            field(
                "alt_up_limit",
                R.string.qbt_alt_up_limit,
                PrefKind.INT,
                def = 0,
                unit = PrefUnit.KIB_PER_SEC,
                blankKeepsValue = true,
            ),
            header(R.string.qbt_speed_sched),
            field("scheduler_enabled", R.string.qbt_sched_enabled, PrefKind.BOOL, def = false),
            field("schedule_from_hour", R.string.qbt_sched_hour_hint, PrefKind.INT, def = 8, min = 0L, max = 23L),
            field("schedule_from_min", R.string.qbt_sched_min_hint, PrefKind.INT, def = 0, min = 0L, max = 59L),
            field("schedule_to_hour", R.string.qbt_sched_hour_hint, PrefKind.INT, def = 20, min = 0L, max = 23L),
            field("schedule_to_min", R.string.qbt_sched_min_hint, PrefKind.INT, def = 0, min = 0L, max = 59L),
            field(
                "scheduler_days",
                R.string.qbt_sched_days,
                PrefKind.DROPDOWN,
                def = 0,
                options = listOf(opt(0, R.string.qbt_sched_day_everyday), opt(1, R.string.qbt_sched_day_weekday), opt(2, R.string.qbt_sched_day_weekend)),
            ),
            header(R.string.qbt_speed_misc),
            field(
                "bittorrent_protocol",
                R.string.qbt_protocol,
                PrefKind.DROPDOWN,
                def = 0,
                options = listOf(opt(0, R.string.qbt_protocol_both), opt(1, R.string.qbt_protocol_tcp), opt(2, R.string.qbt_protocol_utp)),
            ),
            field("limit_utp_rate", R.string.qbt_utp_rate, PrefKind.BOOL, def = false),
            field("limit_tcp_overhead", R.string.qbt_tcp_overhead, PrefKind.BOOL, def = false),
            field("limit_lan_peers", R.string.qbt_lan_peers, PrefKind.BOOL, def = true),
        )),
        PrefSection(R.string.qbt_tab_bittorrent, listOf(
            header(R.string.qbt_bt_privacy),
            field("dht", R.string.qbt_dht, PrefKind.BOOL, def = true),
            field("pex", R.string.qbt_pex, PrefKind.BOOL, def = true),
            field("lsd", R.string.qbt_lsd, PrefKind.BOOL, def = true),
            field(
                "encryption",
                R.string.qbt_encryption,
                PrefKind.DROPDOWN,
                def = 0,
                options = listOf(opt(0, R.string.qbt_encryption_prefer), opt(1, R.string.qbt_encryption_require), opt(2, R.string.qbt_encryption_disable)),
            ),
            field("anonymous_mode", R.string.qbt_anonymous, PrefKind.BOOL, def = false),
            header(R.string.qbt_bt_queue),
            field("queueing_enabled", R.string.qbt_queueing, PrefKind.BOOL, def = true),
            field("max_active_downloads", R.string.qbt_max_active_downloads, PrefKind.INT, def = 3, min = 0L),
            field("max_active_torrents", R.string.qbt_max_active_torrents, PrefKind.INT, def = 5, min = 0L),
            field("max_active_uploads", R.string.qbt_max_active_uploads, PrefKind.INT, def = 3, min = 0L),
            field("max_active_checking_torrents", R.string.qbt_max_active_checking, PrefKind.INT, def = 1, min = 0L),
            field("dont_count_slow_torrents", R.string.qbt_dont_count_slow, PrefKind.BOOL, def = false),
            header(R.string.qbt_bt_seed),
            field("max_ratio_enabled", R.string.qbt_max_ratio_enabled, PrefKind.BOOL, def = false),
            field("max_ratio", R.string.qbt_max_ratio, PrefKind.FLOAT, def = -1.0, min = 0L),
            field("max_seeding_time_enabled", R.string.qbt_max_seeding_time_enabled, PrefKind.BOOL, def = false),
            field("max_seeding_time", R.string.qbt_max_seeding_time, PrefKind.INT, def = 1440, min = 0L),
            field(
                "max_ratio_act",
                R.string.qbt_ratio_act,
                PrefKind.DROPDOWN,
                def = 0,
                options = listOf(opt(0, R.string.qbt_ratio_act_stop), opt(1, R.string.qbt_ratio_act_remove), opt(2, R.string.qbt_ratio_act_superseeding), opt(3, R.string.qbt_ratio_act_remove_content)),
            ),
            header(R.string.qbt_bt_trackers),
            field("add_trackers_enabled", R.string.qbt_add_trackers_enabled, PrefKind.BOOL, def = false),
            field("add_trackers", R.string.qbt_add_trackers, PrefKind.MULTILINE),
            field(
                "slow_torrent_dl_rate_threshold",
                R.string.qbt_slow_dl_rate_kib,
                PrefKind.INT,
                def = 2,
                unit = PrefUnit.KIB_PER_SEC,
                blankKeepsValue = true,
            ),
            field(
                "slow_torrent_ul_rate_threshold",
                R.string.qbt_slow_ul_rate_kib,
                PrefKind.INT,
                def = 2,
                unit = PrefUnit.KIB_PER_SEC,
                blankKeepsValue = true,
            ),
            field("slow_torrent_inactive_timer", R.string.qbt_slow_inactive_timer, PrefKind.INT, def = 60, min = 1L),
            field("reannounce_when_address_changed", R.string.qbt_reannounce_addr_changed, PrefKind.BOOL, def = true),
            field(
                "max_inactive_seeding_time_enabled",
                R.string.qbt_max_inactive_seeding_enabled,
                PrefKind.BOOL,
                def = false,
            ),
            field(
                "max_inactive_seeding_time",
                R.string.qbt_max_inactive_seeding_time,
                PrefKind.INT,
                def = 60,
                min = 0L,
            ),
            header(R.string.qbt_bt_tracker_section),
            field("enable_embedded_tracker", R.string.qbt_embedded_tracker, PrefKind.BOOL, def = false),
            field(
                "embedded_tracker_port",
                R.string.qbt_embedded_tracker_port,
                PrefKind.INT,
                def = 9000,
                min = 1L,
                max = 65535L,
            ),
            field("embedded_tracker_port_forwarding", R.string.qbt_embedded_tracker_fwd, PrefKind.BOOL, def = false),
            header(R.string.qbt_ip_filter_section),
            field("ip_filter_enabled", R.string.qbt_ip_filter_enabled, PrefKind.BOOL, def = false),
            field("ip_filter_path", R.string.qbt_ip_filter_path, PrefKind.TEXT),
            field("ip_filter_trackers", R.string.qbt_ip_filter_trackers, PrefKind.BOOL, def = false),
            field("banned_IPs", R.string.qbt_banned_ips, PrefKind.MULTILINE),
            field("shadow_ban_enabled", R.string.qbt_shadow_ban_enabled, PrefKind.BOOL, def = false),
            field("shadow_banned_IPs", R.string.qbt_shadow_banned_ips, PrefKind.MULTILINE),
            header(R.string.qbt_section_announce),
            field("announce_ip", R.string.qbt_announce_ip, PrefKind.TEXT),
            field("announce_port", R.string.qbt_announce_port, PrefKind.INT, def = 0, min = 0L, max = 65535L),
            header(R.string.qbt_section_extra_trackers),
            field("add_trackers_from_url_enabled", R.string.qbt_add_trackers_from_url, PrefKind.BOOL, def = false),
            field("add_trackers_url", R.string.qbt_add_trackers_url, PrefKind.MULTILINE),
            header(R.string.qbt_section_dht),
            field("dht_bootstrap_nodes", R.string.qbt_dht_bootstrap_nodes, PrefKind.MULTILINE),
            header(R.string.qbt_section_bt_ssl),
            field("ssl_enabled", R.string.qbt_bt_ssl_enabled, PrefKind.BOOL, def = false),
            field("ssl_listen_port", R.string.qbt_bt_ssl_port, PrefKind.INT, def = 0, min = 0L, max = 65535L),
        )),
        PrefSection(R.string.qbt_tab_connection, listOf(
            header(R.string.qbt_conn_listen),
            field("listen_port", R.string.qbt_listen_port, PrefKind.INT, def = 6881, min = 1L, max = 65535L),
            field("upnp", R.string.qbt_upnp, PrefKind.BOOL, def = true),
            header(R.string.qbt_conn_limits),
            field("max_connec", R.string.qbt_max_connec, PrefKind.INT, def = 500),
            field("max_connec_per_torrent", R.string.qbt_max_connec_per_torrent, PrefKind.INT, def = 100),
            field("max_uploads", R.string.qbt_max_uploads, PrefKind.INT, def = -1),
            field("max_uploads_per_torrent", R.string.qbt_max_uploads_per_torrent, PrefKind.INT, def = -1),
            header(R.string.qbt_proxy),
            field(
                "proxy_type",
                R.string.qbt_proxy_type,
                PrefKind.DROPDOWN,
                def = "None",
                legacyNumeric = true,
                options = listOf(opt("None", R.string.qbt_proxy_none), opt("HTTP", R.string.qbt_proxy_http), opt("SOCKS5", R.string.qbt_proxy_socks5), opt("SOCKS4", R.string.qbt_proxy_socks4)),
            ),
            field("proxy_ip", R.string.qbt_proxy_ip, PrefKind.TEXT),
            field("proxy_port", R.string.qbt_proxy_port, PrefKind.INT, def = 0, min = 0L, max = 65535L),
            field("proxy_auth_enabled", R.string.qbt_proxy_auth, PrefKind.BOOL, def = false),
            field("proxy_username", R.string.qbt_proxy_username, PrefKind.TEXT),
            field("proxy_password", R.string.qbt_proxy_password, PrefKind.TEXT),
            field("proxy_bittorrent", R.string.qbt_proxy_bt, PrefKind.BOOL, def = false),
            field("proxy_peer_connections", R.string.qbt_proxy_peers, PrefKind.BOOL, def = false),
            field("proxy_misc", R.string.qbt_proxy_misc, PrefKind.BOOL, def = true),
            header(R.string.qbt_conn_peers),
            field("auto_ban_unknown_peer", R.string.qbt_ban_unknown, PrefKind.BOOL, def = false),
            field("auto_ban_bt_player_peer", R.string.qbt_ban_btplayer, PrefKind.BOOL, def = false),
            field("proxy_hostname_lookup", R.string.qbt_proxy_hostname_lookup, PrefKind.BOOL, def = false),
            field("proxy_rss", R.string.qbt_proxy_rss, PrefKind.BOOL, def = false),
            field("ignore_ssl_errors", R.string.qbt_ignore_ssl_errors, PrefKind.BOOL, def = false),
            header(R.string.qbt_ifaces_section),
            field("random_port", R.string.qbt_random_port, PrefKind.BOOL, def = true),
            field("current_network_interface", R.string.qbt_network_interface, PrefKind.TEXT),
            field("current_interface_address", R.string.qbt_interface_address, PrefKind.TEXT),
            header(R.string.qbt_i2p_section),
            field("i2p_enabled", R.string.qbt_i2p_enabled, PrefKind.BOOL, def = false),
            field("i2p_address", R.string.qbt_i2p_address, PrefKind.TEXT),
            field("i2p_port", R.string.qbt_i2p_port, PrefKind.INT, def = 7656, min = 0L, max = 65535L),
            field("i2p_mixed_mode", R.string.qbt_i2p_mixed, PrefKind.BOOL, def = false),
            field(
                "i2p_inbound_quantity",
                R.string.qbt_i2p_inbound_quantity,
                PrefKind.INT,
                def = 3,
                min = 0L,
                max = 16L,
            ),
            field(
                "i2p_outbound_quantity",
                R.string.qbt_i2p_outbound_quantity,
                PrefKind.INT,
                def = 3,
                min = 0L,
                max = 16L,
            ),
            field("i2p_inbound_length", R.string.qbt_i2p_inbound_length, PrefKind.INT, def = 3, min = 0L, max = 7L),
            field("i2p_outbound_length", R.string.qbt_i2p_outbound_length, PrefKind.INT, def = 3, min = 0L, max = 7L),
            field("connection_speed", R.string.qbt_connection_speed, PrefKind.INT, def = 0, min = 0L),
            field("outgoing_ports_min", R.string.qbt_outgoing_ports_min, PrefKind.INT, def = 0, min = 0L, max = 65535L),
            field("outgoing_ports_max", R.string.qbt_outgoing_ports_max, PrefKind.INT, def = 0, min = 0L, max = 65535L),
            field("upnp_lease_duration", R.string.qbt_upnp_lease, PrefKind.INT, def = 0, min = 0L),
            field("peer_tos", R.string.qbt_peer_tos, PrefKind.INT, def = 0),
            header(R.string.qbt_section_dyndns),
            field("dyndns_enabled", R.string.qbt_dyndns_enabled, PrefKind.BOOL, def = false),
            field("dyndns_domain", R.string.qbt_dyndns_domain, PrefKind.TEXT),
            field("dyndns_username", R.string.qbt_dyndns_username, PrefKind.TEXT),
            field("dyndns_password", R.string.qbt_dyndns_password, PrefKind.TEXT),
            field(
                "dyndns_service",
                R.string.qbt_dyndns_service,
                PrefKind.DROPDOWN,
                def = -1,
                options = listOf(opt(-1, R.string.qbt_dyndns_none), opt(0, R.string.qbt_dyndns_dyndns), opt(1, R.string.qbt_dyndns_noip)),
            ),
        )),
        PrefSection(R.string.qbt_tab_webui, listOf(
            header(R.string.qbt_webui_http),
            field("web_ui_address", R.string.qbt_webui_address, PrefKind.TEXT),
            field("web_ui_port", R.string.qbt_webui_port, PrefKind.INT, def = 8080, min = 1L, max = 65535L),
            field("web_ui_upnp", R.string.qbt_webui_upnp, PrefKind.BOOL, def = false),
            field(
                "web_ui_host_header_validation_enabled",
                R.string.qbt_webui_host_validation,
                PrefKind.BOOL,
                def = true,
            ),
            header(R.string.qbt_webui_auth),
            field("web_ui_username", R.string.qbt_webui_username, PrefKind.TEXT, def = admin, blankKeepsValue = true),
            field("web_ui_password", R.string.qbt_webui_password, PrefKind.PASSWORD, def = , blankKeepsValue = true),
            field("bypass_local_auth", R.string.qbt_webui_bypass_local, PrefKind.BOOL, def = false),
            header(R.string.qbt_webui_auth_section),
            field(
                "bypass_auth_subnet_whitelist_enabled",
                R.string.qbt_bypass_subnet_enabled,
                PrefKind.BOOL,
                def = false,
            ),
            field("bypass_auth_subnet_whitelist", R.string.qbt_bypass_subnet, PrefKind.TEXT),
            field("web_ui_max_auth_fail_count", R.string.qbt_max_auth_fails, PrefKind.INT, def = 5, min = 0L),
            field("web_ui_ban_duration", R.string.qbt_ban_duration_sec, PrefKind.INT, def = 3600, min = 0L),
            field("web_ui_session_timeout", R.string.qbt_session_timeout_sec, PrefKind.INT, def = 3600, min = 0L),
            header(R.string.qbt_webui_security_section),
            field("web_ui_csrf_protection_enabled", R.string.qbt_csrf_protection, PrefKind.BOOL, def = true),
            field(
                "web_ui_clickjacking_protection_enabled",
                R.string.qbt_clickjacking_protection,
                PrefKind.BOOL,
                def = true,
            ),
            field("web_ui_secure_cookie_enabled", R.string.qbt_secure_cookie, PrefKind.BOOL, def = true),
            field("web_ui_domain_list", R.string.qbt_domain_list, PrefKind.TEXT),
            header(R.string.qbt_webui_custom_section),
            field("alternative_webui_enabled", R.string.qbt_alt_webui_enabled, PrefKind.BOOL, def = false),
            field("alternative_webui_path", R.string.qbt_alt_webui_path, PrefKind.TEXT),
            field("web_ui_reverse_proxy_enabled", R.string.qbt_reverse_proxy_enabled, PrefKind.BOOL, def = false),
            field("web_ui_reverse_proxies_list", R.string.qbt_reverse_proxies_list, PrefKind.TEXT),
            field(
                "web_ui_use_custom_http_headers_enabled",
                R.string.qbt_custom_headers_enabled,
                PrefKind.BOOL,
                def = false,
            ),
            field(
                "web_ui_custom_http_headers",
                R.string.qbt_custom_headers,
                PrefKind.MULTILINE,
                codec = PrefCodec.HTTP_HEADERS,
            ),
            header(R.string.qbt_webui_https_section),
            field("use_https", R.string.qbt_use_https, PrefKind.BOOL, def = false),
            field("web_ui_https_cert_path", R.string.qbt_https_cert_path, PrefKind.TEXT),
            field("web_ui_https_key_path", R.string.qbt_https_key_path, PrefKind.TEXT),
            field("locale", R.string.qbt_webui_locale, PrefKind.TEXT),
            field("refresh_interval", R.string.qbt_webui_refresh, PrefKind.INT, def = 1500, min = 0L),
            field("app_instance_name", R.string.qbt_app_instance_name, PrefKind.TEXT),
            field("ssrf_mitigation", R.string.qbt_ssrf_mitigation, PrefKind.BOOL, def = true),
            field("status_bar_external_ip", R.string.qbt_statusbar_ip, PrefKind.BOOL, def = true),
            field("performance_warning", R.string.qbt_perf_warning, PrefKind.BOOL, def = false),
        )),
        PrefSection(R.string.qbt_tab_rss, listOf(
            header(R.string.qbt_rss_feeds_section),
            field(
                "rss_refresh_interval",
                R.string.qbt_rss_refresh_interval_sec,
                PrefKind.INT,
                def = 600,
                min = 1L,
                max = 9999999L,
            ),
            field(
                "rss_fetch_delay",
                R.string.qbt_rss_fetch_delay_sec,
                PrefKind.LONG,
                def = 600L,
                min = 0L,
                max = 9999999L,
            ),
            field(
                "rss_max_articles_per_feed",
                R.string.qbt_rss_max_articles,
                PrefKind.INT,
                def = 50,
                min = 1L,
                max = 9999L,
            ),
            header(R.string.qbt_rss_auto_section),
            field("rss_processing_enabled", R.string.qbt_rss_processing_enabled, PrefKind.BOOL, def = true),
            field("rss_auto_downloading_enabled", R.string.qbt_rss_auto_downloading_enabled, PrefKind.BOOL, def = true),
            field("rss_download_repack_proper_episodes", R.string.qbt_rss_repack_proper, PrefKind.BOOL, def = true),
            field("rss_smart_episode_filters", R.string.qbt_rss_smart_filters, PrefKind.MULTILINE),
        )),
        PrefSection(R.string.qbt_tab_advanced, listOf(
            field("async_io_threads", R.string.qbt_async_io_threads, PrefKind.INT, def = 10, min = 1L, max = 1024L),
            field("hashing_threads", R.string.qbt_hashing_threads, PrefKind.INT, def = 2, min = 1L, max = 1024L),
            field("checking_memory_use", R.string.qbt_checking_mem, PrefKind.INT, def = 16, min = 16L, max = 4096L),
            field("announce_to_all_trackers", R.string.qbt_announce_all, PrefKind.BOOL, def = false),
            field("announce_to_all_tiers", R.string.qbt_announce_all_tiers, PrefKind.BOOL, def = false),
            header(R.string.qbt_adv_storage_section),
            field(
                "resume_data_storage_type",
                R.string.qbt_resume_data_storage,
                PrefKind.DROPDOWN,
                def = "SQLite",
                options = listOf(opt("SQLite", R.string.qbt_resume_storage_sqlite), opt("Legacy", R.string.qbt_resume_storage_legacy)),
            ),
            field("save_resume_data_interval", R.string.qbt_save_resume_interval_min, PrefKind.INT, def = 60, min = 0L),
            field("memory_working_set_limit", R.string.qbt_memory_working_set_mb, PrefKind.INT, def = 512, min = 256L),
            field(
                "torrent_file_size_limit",
                R.string.qbt_torrent_file_size_limit_mb,
                PrefKind.LONG,
                def = 100L,
                min = 0L,
            ),
            header(R.string.qbt_adv_files_section),
            field("excluded_file_names_enabled", R.string.qbt_excluded_names_enabled, PrefKind.BOOL, def = false),
            field("excluded_file_names", R.string.qbt_excluded_names, PrefKind.MULTILINE),
            field("python_executable_path", R.string.qbt_python_path, PrefKind.TEXT),
            header(R.string.qbt_adv_behavior_section),
            field("confirm_torrent_deletion", R.string.qbt_confirm_deletion, PrefKind.BOOL, def = true),
            field("confirm_torrent_recheck", R.string.qbt_confirm_recheck, PrefKind.BOOL, def = true),
            field("delete_torrent_content_files", R.string.qbt_delete_content_default, PrefKind.BOOL, def = false),
            field("resolve_peer_countries", R.string.qbt_resolve_peer_countries, PrefKind.BOOL, def = true),
            field("resolve_peer_host_names", R.string.qbt_resolve_peer_hostnames, PrefKind.BOOL, def = false),
            header(R.string.qbt_adv_log_section),
            field("file_log_enabled", R.string.qbt_file_log_enabled, PrefKind.BOOL, def = true),
            field("file_log_path", R.string.qbt_file_log_path, PrefKind.TEXT),
            field("file_log_backup_enabled", R.string.qbt_file_log_backup, PrefKind.BOOL, def = false),
            field(
                "file_log_max_size",
                R.string.qbt_file_log_max_size_kib,
                PrefKind.INT,
                def = 65,
                min = 1L,
                max = 1048576L,
            ),
            field("file_log_delete_old", R.string.qbt_file_log_delete_old, PrefKind.BOOL, def = true),
            field("file_log_age", R.string.qbt_file_log_age, PrefKind.INT, def = 1, min = 1L, max = 3650L),
            field(
                "file_log_age_type",
                R.string.qbt_file_log_age_type,
                PrefKind.DROPDOWN,
                def = 1,
                options = listOf(opt(0, R.string.qbt_log_age_days), opt(1, R.string.qbt_log_age_months), opt(2, R.string.qbt_log_age_years)),
            ),
            header(R.string.qbt_section_disk_io),
            field(
                "disk_io_type",
                R.string.qbt_disk_io_type,
                PrefKind.DROPDOWN,
                def = 0,
                options = listOf(opt(0, R.string.qbt_disk_io_default), opt(1, R.string.qbt_disk_io_mmap), opt(2, R.string.qbt_disk_io_posix), opt(3, R.string.qbt_disk_io_pread)),
            ),
            field(
                "disk_io_read_mode",
                R.string.qbt_disk_io_read_mode,
                PrefKind.DROPDOWN,
                def = 0,
                options = listOf(opt(0, R.string.qbt_os_cache_disable), opt(1, R.string.qbt_os_cache_enable)),
            ),
            field(
                "disk_io_write_mode",
                R.string.qbt_disk_io_write_mode,
                PrefKind.DROPDOWN,
                def = 1,
                options = listOf(opt(0, R.string.qbt_os_cache_disable), opt(1, R.string.qbt_os_cache_enable)),
            ),
            header(R.string.qbt_section_libtorrent_limits),
            field("file_pool_size", R.string.qbt_file_pool_size, PrefKind.INT, def = 40, min = 0L),
            field("request_queue_size", R.string.qbt_request_queue_size, PrefKind.INT, def = 500, min = 0L),
            field("save_statistics_interval", R.string.qbt_save_stats_interval, PrefKind.INT, def = 60, min = 0L),
            header(R.string.qbt_section_memory_legacy),
            field("disk_cache", R.string.qbt_disk_cache, PrefKind.INT, def = -1),
            field("disk_cache_ttl", R.string.qbt_disk_cache_ttl, PrefKind.INT, def = 60, min = 0L),
            field("disk_queue_size", R.string.qbt_disk_queue_size, PrefKind.LONG, def = 1024L, min = 0L),
            header(R.string.qbt_section_socket),
            field("send_buffer_watermark", R.string.qbt_send_buffer_watermark, PrefKind.INT, def = 500, min = 0L),
            field(
                "send_buffer_low_watermark",
                R.string.qbt_send_buffer_low_watermark,
                PrefKind.INT,
                def = 10,
                min = 0L,
            ),
            field(
                "send_buffer_watermark_factor",
                R.string.qbt_send_buffer_watermark_factor,
                PrefKind.INT,
                def = 50,
                min = 0L,
            ),
            field("socket_backlog_size", R.string.qbt_socket_backlog, PrefKind.INT, def = 10, min = 0L),
            field("socket_send_buffer_size", R.string.qbt_socket_send_buffer, PrefKind.INT, def = 0),
            field("socket_receive_buffer_size", R.string.qbt_socket_receive_buffer, PrefKind.INT, def = 0),
            header(R.string.qbt_section_libtorrent_ext),
            field("max_concurrent_http_announces", R.string.qbt_max_http_announces, PrefKind.INT, def = 50, min = 0L),
            field("stop_tracker_timeout", R.string.qbt_stop_tracker_timeout, PrefKind.INT, def = 2, min = 0L),
            field("peer_turnover", R.string.qbt_peer_turnover, PrefKind.INT, def = 8, min = 0L),
            field("peer_turnover_interval", R.string.qbt_peer_turnover_interval, PrefKind.INT, def = 300, min = 0L),
            field("peer_turnover_cutoff", R.string.qbt_peer_turnover_cutoff, PrefKind.INT, def = 90, min = 0L),
            field("bdecode_depth_limit", R.string.qbt_bdecode_depth, PrefKind.INT, def = 100, min = 0L),
            field("bdecode_token_limit", R.string.qbt_bdecode_token, PrefKind.LONG, def = 10000000L, min = 0L),
            field("hostname_cache_ttl", R.string.qbt_hostname_cache_ttl, PrefKind.INT, def = 300, min = 0L),
            field(
                "utp_tcp_mixed_mode",
                R.string.qbt_utp_mixed_mode,
                PrefKind.DROPDOWN,
                def = 0,
                options = listOf(opt(0, R.string.qbt_utp_prefer_tcp), opt(1, R.string.qbt_utp_proportional)),
            ),
            field(
                "upload_slots_behavior",
                R.string.qbt_upload_slots_behavior,
                PrefKind.DROPDOWN,
                def = 0,
                options = listOf(opt(0, R.string.qbt_slots_fixed), opt(1, R.string.qbt_slots_rate_based)),
            ),
            field(
                "upload_choking_algorithm",
                R.string.qbt_upload_choking_algorithm,
                PrefKind.DROPDOWN,
                def = 1,
                options = listOf(opt(0, R.string.qbt_seed_round_robin), opt(1, R.string.qbt_seed_fastest_upload), opt(2, R.string.qbt_seed_anti_leech)),
            ),
            field("enable_coalesce_read_write", R.string.qbt_coalesce_read_write, PrefKind.BOOL, def = false),
            field("enable_multi_connections_from_same_ip", R.string.qbt_multi_connections, PrefKind.BOOL, def = false),
            field("enable_piece_extent_affinity", R.string.qbt_piece_extent_affinity, PrefKind.BOOL, def = false),
            field("enable_upload_suggestions", R.string.qbt_upload_suggestions, PrefKind.BOOL, def = false),
            field("block_peers_on_privileged_ports", R.string.qbt_block_privileged_ports, PrefKind.BOOL, def = false),
            field("validate_https_tracker_certificate", R.string.qbt_validate_https_tracker, PrefKind.BOOL, def = true),
            field("idn_support_enabled", R.string.qbt_idn_support, PrefKind.BOOL, def = false),
        )),
    )

    /** Known fields indexed by preference key. */
    val byKey: Map<String, PrefField> by lazy {
        sections.asSequence()
            .flatMap { it.entries.asSequence() }
            .filterIsInstance<PrefEntry.Row>()
            .associate { it.field.key to it.field }
    }

    /**
     * Derives a field description for a preference key this app does not
     * know yet (added by a newer qBittorrent). The control kind follows the
     * JSON type the server reports, so bools arrive as switches, numbers as
     * numeric fields and structured values as raw-JSON text.
     */
    fun inferField(key: String, value: JsonElement?): PrefField {
        val kind = when {
            value == null -> PrefKind.TEXT
            !value.isJsonPrimitive -> PrefKind.MULTILINE
            value.asJsonPrimitive.isBoolean -> PrefKind.BOOL
            value.asJsonPrimitive.isNumber -> {
                val d = value.asJsonPrimitive.asDouble
                if (d.isNaN() || d == Math.floor(d)) PrefKind.INT else PrefKind.FLOAT
            }
            else -> PrefKind.TEXT
        }
        return PrefField(key = key, label = 0, kind = kind, def = null).apply {
            labelText = prettifyKey(key)
        }
    }

    /** "proxy_type" -> "Proxy type" for unknown keys. */
    fun prettifyKey(key: String): String =
        key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
