package com.gmail.manhle240406.project.community;

import android.content.Context;

import com.gmail.manhle240406.project.R;

public final class DailyCopy {
    private DailyCopy() {}

    public static String reportStatus(Context c, Object status) {
        int id;
        String s = status instanceof String ? (String) status : "";
        switch (s) {
            case "resolved_no_action":
                id = R.string.permissions_report_no_action_done;
                break;
            case "action_recorded":
                id = R.string.permissions_report_action_done;
                break;
            case "needs_specialist":
                id = R.string.permissions_report_needs_support;
                break;
            case "in_review":
                id = R.string.report_in_review;
                break;
            case "needs_information":
                id = R.string.report_needs_information;
                break;
            case "resolved":
                id = R.string.report_resolved;
                break;
            case "rejected":
                id = R.string.report_rejected;
                break;
            case "duplicate":
                id = R.string.report_duplicate;
                break;
            default:
                id = R.string.report_received;
        }
        return c.getString(id);
    }

    public static String notice(Context c, String n) {
        int id;
        switch (n) {
            case "queued":
                id = R.string.community_pending;
                break;
            case "local_failed":
                id = R.string.community_local_failed;
                break;
            case "sending":
                id = R.string.social_sending;
                break;
            case "committed":
                id = R.string.social_committed;
                break;
            case "unknown":
                id = R.string.social_unknown;
                break;
            case "conflict":
                id = R.string.social_conflict;
                break;
            case "rate":
                id = R.string.report_rate;
                break;
            case "duplicate":
                id = R.string.report_duplicate;
                break;
            case "not_committed":
                id = R.string.social_not_committed;
                break;
            case "lost_text":
                id = R.string.social_lost_text;
                break;
            case "unavailable":
                id = R.string.social_unavailable;
                break;
            case "account_not_ready":
                id = R.string.report_account_not_ready;
                break;
            case "report_permission":
                id = R.string.report_permission_denied;
                break;
            case "network_unavailable":
                id = R.string.report_network_unavailable;
                break;
            default:
                id = R.string.social_service;
        }
        return c.getString(id);
    }
}
