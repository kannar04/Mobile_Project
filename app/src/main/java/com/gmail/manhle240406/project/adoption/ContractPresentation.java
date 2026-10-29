package com.gmail.manhle240406.project.adoption;

import org.json.*;

import java.util.*;

/** Presentation of the immutable readable snapshot; never changes signed content or its hash. */
final class ContractPresentation {
    static final class Block {
        final String text;
        final boolean heading;

        Block(String text, boolean heading) {
            this.text = text;
            this.heading = heading;
        }
    }

    static List<Block> blocks(android.content.Context context, String readable) {
        List<Block> result = new ArrayList<>();
        int start = readable.indexOf("\n\n{");
        if (start < 0) {
            result.add(new Block(readable, false));
            return result;
        }
        try {
            JSONObject fields = new JSONObject(readable.substring(start + 2));
            result.add(new Block(readable.substring(0, start), false));
            append(context, fields, result, 0);
        } catch (JSONException invalid) {
            result.clear();
            result.add(new Block(readable, false));
        }
        return result;
    }

    private static void append(
            android.content.Context context, JSONObject fields, List<Block> out, int depth)
            throws JSONException {
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = fields.keys(); it.hasNext(); ) keys.add(it.next());
        List<String> priority =
                Arrays.asList(
                        "title",
                        "displayName",
                        "description",
                        "temperament",
                        "careRequirements",
                        "adoptionRequirements",
                        "identitySnapshot",
                        "careSnapshot",
                        "assetIds");
        keys.sort(
                Comparator.comparingInt(
                        k -> priority.contains(k) ? priority.indexOf(k) : priority.size()));
        for (String key : keys) {
            Object value = fields.get(key);
            String label = label(context, key);
            if (value instanceof JSONObject && ((JSONObject) value).has("_seconds")) {
                long seconds = ((JSONObject) value).optLong("_seconds");
                out.add(
                        new Block(
                                label
                                        + "\n"
                                        + java.text.DateFormat.getDateTimeInstance()
                                                .format(new Date(seconds * 1000)),
                                false));
                continue;
            }
            if (value instanceof JSONObject && depth < 8) {
                out.add(new Block(label, true));
                append(context, (JSONObject) value, out, depth + 1);
            } else if (value instanceof JSONArray) {
                JSONArray list = (JSONArray) value;
                out.add(new Block(label, true));
                if (list.length() == 0)
                    out.add(
                            new Block(
                                    context.getString(
                                            com.gmail.manhle240406.project.R.string.contract_empty),
                                    false));
                for (int i = 0; i < list.length(); i++) {
                    Object item = list.get(i);
                    if (item instanceof JSONObject && depth < 8)
                        append(context, (JSONObject) item, out, depth + 1);
                    else out.add(new Block("• " + plain(context, item), false));
                }
            } else out.add(new Block(label + "\n" + plain(context, value), false));
        }
    }

    private static String plain(android.content.Context context, Object value) {
        if (value == JSONObject.NULL || "".equals(value))
            return context.getString(com.gmail.manhle240406.project.R.string.contract_empty);
        if (value instanceof Boolean)
            return (Boolean) value
                    ? context.getString(com.gmail.manhle240406.project.R.string.contract_yes)
                    : context.getString(com.gmail.manhle240406.project.R.string.contract_no);
        return String.valueOf(value);
    }

    private static String label(android.content.Context context, String key) {
        switch (key) {
            case "title":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_0);
            case "description":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_1);
            case "temperament":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_2);
            case "careRequirements":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_3);
            case "adoptionRequirements":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_4);
            case "identitySnapshot":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_5);
            case "careSnapshot":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_6);
            case "displayName":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_7);
            case "species":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_8);
            case "breed":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_9);
            case "sex":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_10);
            case "ageMonths":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_11);
            case "ageObservedAt":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_12);
            case "marks":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_13);
            case "marksUnknown":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_14);
            case "healthState":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_15);
            case "behaviorNotes":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_16);
            case "careNeeds":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_17);
            case "sterilization":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_18);
            case "vaccinations":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_19);
            case "color":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_20);
            case "assetIds":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_21);
            case "regionCode":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_22);
            case "listingId":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_23);
            case "petId":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_24);
            case "ownerUid":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_25);
            case "contentVersion":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_26);
            case "schemaVersion":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_27);
            case "publicationVersion":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_28);
            case "updatedAt":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_29);
            case "firstPublishedAt":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_30);
            case "careVersion":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_31);
            case "careDigest":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_32);
            case "sourceFingerprint":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_33);
            case "availability":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_34);
            case "state":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_35);
            case "audience":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_36);
            case "bundledIllustrationKey":
            case "illustrationKey":
                return context.getString(com.gmail.manhle240406.project.R.string.contract_label_37);
            default:
                return key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        }
    }
}
