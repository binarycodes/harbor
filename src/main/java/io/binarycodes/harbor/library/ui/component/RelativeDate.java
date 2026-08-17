package io.binarycodes.harbor.library.ui.component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.ibm.icu.text.DateFormat;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;

/**
 * How long ago something was saved, phrased the way a reader skimming a list
 * wants it: "today" and "3d ago" for the recent past, an actual date once the
 * relative form stops being informative.
 */
public final class RelativeDate {

    private static final long WEEK_IN_DAYS = 7;
    private static final long MONTH_IN_DAYS = 28;

    private RelativeDate() {
    }

    public static String label(Component context, long epochMillis) {
        long days = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now()).toDays();
        if (days <= 0) {
            return context.getTranslation("date.today");
        }
        if (days == 1) {
            return context.getTranslation("date.yesterday");
        }
        if (days < WEEK_IN_DAYS) {
            return context.getTranslation("date.days", days);
        }
        if (days < MONTH_IN_DAYS) {
            return context.getTranslation("date.weeks", days / WEEK_IN_DAYS);
        }
        return DateFormat
                .getInstanceForSkeleton(DateFormat.ABBR_MONTH_DAY, UI.getCurrent().getLocale())
                .format(new Date(epochMillis));
    }
}
