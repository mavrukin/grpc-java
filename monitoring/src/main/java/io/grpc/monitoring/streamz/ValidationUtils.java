package io.grpc.monitoring.streamz;

import com.google.common.base.Ascii;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

class ValidationUtils {

    private ValidationUtils() {}  // Do not instantiate.

    /**
     * Matches a valid path. There are two considerations:
     *
     * (1) RFC 1738, section 2.2:
     *
     *   Thus, only alphanumerics, the special characters "$-_.+!*'(),", and
     *   reserved characters used for their reserved purposes may be used
     *   unencoded within a URL.
     *
     * We check separately that all escaped octets are valid so % is effectively
     * now valid as well. We also don't want to accept any of the reserved
     * characters except "/" (the others are ";?:@=&").
     *
     * (2) RFC 1738, section 2.3:
     *
     *   Some URL schemes (such as the ftp, http, and file schemes) contain
     *   names that can be considered hierarchical; the components of the
     *   hierarchy are separated by "/".
     *
     * We have no scheme but informally the streamz scheme is hierarchical.
     */
    private static final Pattern PATH_PATTERN =
            Pattern.compile("(/[a-zA-Z0-9$\\-_.+!*'(),%]+)+");

    /**
     * Returns true if this is a normalized hostname. We require lowercase
     * characters and no trailing period.
     */
    private static boolean isNormalizedHostName(String name) {
        for (char c : name.toCharArray()) {
            if (Ascii.isUpperCase(c)) {
                return false;
            }
        }
        return !name.endsWith(".");
    }

    /**
     * Checks if {@code c} is a character which must be escaped according to
     * RFC 1738, i.e., whether a %-encoded octet is allowed to be %-encoded for
     * URL-like metric names, to prevent different legal metric names URL-encoding
     * to the same source string.
     *
     * '/' can actually be used without escaping, but it must be escaped if it is
     * not used as the hierarchical path divider.
     */
    private static boolean mustBeEscapedInUrl(char c) {
        return !(c > 32 && c < 127) ||  // ASCII printable
                // Unsafe:
                c == '<' || c == '>' || c == '"' || c == '#' || c == '%' ||
                c == '{' || c == '}' || c == '|' ||  c == '\\' || c == '^' ||
                c == '~' || c == '[' || c == ']' || c == '`' ||
                // Reserved:
                c == ';' || c == '/' || c == '?' || c == ':' || c == '@' ||
                c == '=' || c == '&';
    }

    private static boolean isHex(char c) {
        return isAsciiDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Validates a URL-like metric name.
     */
    public static void validateUrlLikeMetricName(String name) {
        validateUrlLikeName("metric", name);
    }

    /**
     * Validates a URL-like field name.
     */
    public static void validateUrlLikeFieldName(String name) {
        validateUrlLikeName("field", name);
    }

    private static void validateUrlLikeName(String type, String name) {
        String commonMessage = "Illegal " + type + " name: \"" + name + "\"; ";

        int firstSlash = name.indexOf('/');
        if (firstSlash == -1) {
            throw new IllegalArgumentException(commonMessage + "must contain at least one slash");
        }

        String service = name.substring(0, firstSlash);
        try {
            URI uri = new URI("http://" + service);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException(commonMessage
                        + "service part is not a valid host name under RFC 2396");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(commonMessage
                    + "service part is not a valid host name under RFC 2396", e);
        }

        if (!isNormalizedHostName(service)) {
            throw new IllegalArgumentException(commonMessage
                    + "service part is not normalized (lowercase with no trailing period)");
        }

        String path = name.substring(firstSlash);  // Removes the service part.

        // Make sure that every escaped octet is valid (RFC 1738, section 2.2.).
        //
        // Also allow only normalized paths (%-encoding requires upper case
        // hexadecimal digits, and characters not requiring %-encoding should not be
        // encoded) so that different metric names would always URL-encode different
        // strings.
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '%') {
                // Is the % followed by two hex digits?
                if (!(i < path.length() - 2
                        && isHex(path.charAt(i + 1)) && isHex(path.charAt(i + 2)))) {
                    throw new IllegalArgumentException(commonMessage + "bad escaped octet");
                }

                // Are the hexadecimal digits upper case?
                // RFC 1738 mentions upper case digits first, so consider them primary.
                if (!((isAsciiDigit(path.charAt(i + 1)) || Ascii.isUpperCase(path.charAt(i + 1)))
                        && (isAsciiDigit(path.charAt(i + 2)) || Ascii.isUpperCase(path.charAt(i + 2))))) {
                    throw new IllegalArgumentException(commonMessage
                            + "escaped octet must be normalized to use upper case digits: "
                            + path.substring(i, i + 3));
                }

                // Is the octet one which must be encoded?
                char escaped = (char) Integer.parseInt(path.substring(i + 1, i + 3), 16);
                if (!mustBeEscapedInUrl(escaped)) {
                    throw new IllegalArgumentException(commonMessage
                            + "octet not required to be escaped must be normalized to be used as is: "
                            + path.substring(i, i + 3));
                }
            }
        }

        if (!PATH_PATTERN.matcher(path).matches()) {
            throw new IllegalArgumentException(commonMessage + "invalid path");
        }
    }
}