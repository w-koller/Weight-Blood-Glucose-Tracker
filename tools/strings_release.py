# Copy added for the 1.0.0 release, written to the product-UI rules in VOICE.md: say what the thing is,
# then what the reader can do, then what happens next, in complete sentences with Australian spelling.
STRINGS = {
    # Check-in without a plan
    "checkin_no_plan": "Your plan has not been set up yet. Please set it up in Settings › Your plan, with your doctor’s order in front of you. The check-in then shows the dose for each reading.",

    # Home
    "home_reminders_blocked": "The app cannot show your reminders until you allow notifications. Please allow them, so a reminder appears at each check time.",

    # Assistant answers given by the app, not the model
    "assistant_hypo_title": "Signs of a low blood glucose level",
    "assistant_hypo_answer": "Feeling shaky, sweaty, dizzy or light-headed can be a sign that your blood glucose level is low. Please check your blood glucose level now. Please have some fast-acting sugar, such as half a glass of fruit juice or 6 to 7 jellybeans, if the reading is low, and check your level again in 15 minutes. Please call 000 straight away if you feel very unwell.",
    "assistant_crisis_title": "Someone to talk to",
    "assistant_crisis_answer": "Please call 000 now if you are in danger or might act on these thoughts. Lifeline is there for anyone who needs to talk, at any time of the day or night, on 13 11 14.",
    "assistant_call_lifeline": "Call Lifeline on 13 11 14",

    # Saving the plan
    "setup_save_failed_title": "The plan could not be saved",
    "setup_save_failed": "Something went wrong while saving your plan. Please try again.",

    # Plan setup checks
    "error_times_order": "Please check the times. Each check needs its own time, later in the day than the check above it. Bedtime can be after midnight.",
    "error_hypo_above_range": "Please check your low level. It needs to be the same as, or below, the bottom of your target range.",

    # What a screen reader hears for things that change every second
    "download_progress_spoken": "%1$d%% downloaded",

    # Restoring a backup
    "backup_damaged": "This backup file is damaged, so it cannot be restored. Your records on this phone have not changed. Please try another backup file.",
}

PLURALS = {
    "checkin_low_timer_spoken": {
        "one": "Please check your level again at %1$s, in about %2$d minute.",
        "other": "Please check your level again at %1$s, in about %2$d minutes.",
    },
}
