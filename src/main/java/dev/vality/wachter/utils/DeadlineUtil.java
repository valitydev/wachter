package dev.vality.wachter.utils;

import dev.vality.wachter.exceptions.DeadlineException;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
@SuppressWarnings("ParameterName")
public class DeadlineUtil {

    private static final String FLOATING_NUMBER_REGEXP = "[0-9]+([.][0-9]+)?";
    private static final String MIN_REGEXP = "(?!ms)[m]";
    private static final String SEC_REGEXP = "[s]";
    private static final String MILLISECOND_REGEXP = "[m][s]";

    public static boolean containsRelativeValues(String xRequestDeadline, String xRequestId) {
        return extractMinutes(xRequestDeadline, xRequestId) + extractSeconds(xRequestDeadline, xRequestId) +
                extractMilliseconds(xRequestDeadline, xRequestId) > 0;
    }

    public static Long extractMinutes(String xRequestDeadline, String xRequestId) {
        String format = "minutes";

        checkNegativeValues(
                xRequestDeadline,
                xRequestId,
                "([-]" + FLOATING_NUMBER_REGEXP + MIN_REGEXP + ")",
                format);

        Double minutes = extractValue(
                xRequestDeadline,
                "(" + FLOATING_NUMBER_REGEXP + MIN_REGEXP + ")",
                xRequestId,
                format);

        return Optional.ofNullable(minutes).map(min -> min * 60000.0).map(Double::longValue).orElse(0L);
    }

    public static Long extractSeconds(String xRequestDeadline, String xRequestId) {
        String format = "seconds";

        checkNegativeValues(
                xRequestDeadline,
                xRequestId,
                "([-]" + FLOATING_NUMBER_REGEXP + SEC_REGEXP + ")",
                format);

        Double seconds = extractValue(
                xRequestDeadline,
                "(" + FLOATING_NUMBER_REGEXP + SEC_REGEXP + ")",
                xRequestId,
                format);

        return Optional.ofNullable(seconds).map(s -> s * 1000.0).map(Double::longValue).orElse(0L);
    }

    public static Long extractMilliseconds(String xRequestDeadline, String xRequestId) {
        String format = "milliseconds";

        checkNegativeValues(
                xRequestDeadline,
                xRequestId,
                "([-]" + FLOATING_NUMBER_REGEXP + MILLISECOND_REGEXP + ")",
                format);

        Double milliseconds = extractValue(
                xRequestDeadline,
                "(" + FLOATING_NUMBER_REGEXP + MILLISECOND_REGEXP + ")",
                xRequestId,
                format);

        if (milliseconds != null && Math.ceil(milliseconds % 1) > 0) {
            throw new DeadlineException(
                    String.format("Deadline 'milliseconds' parameter can have only integer value, xRequestId=%s ",
                            xRequestId));
        }

        return Optional.ofNullable(milliseconds).map(Double::longValue).orElse(0L);
    }

    private static void checkNegativeValues(String xRequestDeadline, String xRequestId, String regex, String format) {
        if (!match(regex, xRequestDeadline).isEmpty()) {
            throw new DeadlineException(
                    String.format("Deadline '%s' parameter has negative value, xRequestId=%s ", format, xRequestId));
        }
    }

    private static Double extractValue(String xRequestDeadline, String formatRegex, String xRequestId, String format) {
        String numberRegex = "(" + FLOATING_NUMBER_REGEXP + ")";

        List<String> doubles = new ArrayList<>();
        for (String string : match(formatRegex, xRequestDeadline)) {
            doubles.addAll(match(numberRegex, string));
        }
        if (doubles.size() > 1) {
            throw new DeadlineException(
                    String.format("Deadline '%s' parameter has a few relative value, xRequestId=%s ", format,
                            xRequestId));
        }
        if (doubles.isEmpty()) {
            return null;
        }
        return Double.valueOf(doubles.getFirst());
    }

    private static List<String> match(String regex, String data) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(data);
        List<String> strings = new ArrayList<>();
        while (matcher.find()) {
            strings.add(matcher.group());
        }
        return strings;
    }
}
