# Copy for the logbook list, with its chart, and for the LO, HI and time-of-day controls on the blood
# glucose keypad. See strings_core.py for the rules.
STRINGS = {
    # The blood glucose keypad
    "meter_lo": "LO",
    "meter_hi": "HI",
    "checkin_time_of_day": "Time of day",
    "checkin_time_of_day_now": "This reading is %1$s.",

    # The logbook list
    "logbook_today": "Today, %1$s",
    "logbook_yesterday": "Yesterday, %1$s",
    "logbook_show_details": "Show the details",
    "logbook_hide_details": "Hide the details",
    "logbook_details_shown": "Details shown",
    "logbook_details_hidden": "Details hidden",
    "logbook_note": "Note: %1$s",
    "logbook_sentence": "%1$s.",
    "logbook_summary_given": "%2$s of %1$s",
    "logbook_entry_reading": "%1$s, %2$s. %3$s, %4$s.",
    "logbook_entry_time": "%1$s, %2$s.",

    # The chart above the list
    "logbook_chart_show_day": "Show this day on a large chart",
    "logbook_chart_day_none": "%1$s. No readings.",
    "logbook_chart_day_one": "%1$s. One reading of %2$s.",
    "logbook_chart_day_range": "%1$s. %2$s, from %3$s to %4$s.",

    # The full-screen chart of one day
    "day_chart_earlier": "Earlier day",
    "day_chart_later": "Later day",
    "day_chart_none": "There are no readings on this day.",
    "day_chart_detail_title": "%1$s, %2$s",
}

PLURALS = {
    "day_chart_history": {
        "one": "The small dots show %1$d reading from the days before.",
        "other": "The small dots show %1$d readings from the days before.",
    },
    "day_chart_key": {
        "one": "The large values are the readings on this day. The small dots are your readings from the day before, at the same time of day. The shaded band is your target range.",
        "other": "The large values are the readings on this day. The small dots are your readings from the %1$d days before, at the same time of day. The shaded band is your target range.",
    },
    "logbook_summary_others": {"one": "%1$d other medicine", "other": "%1$d other medicines"},
    "logbook_summary_medicines": {"one": "%1$d medicine", "other": "%1$d medicines"},
}
