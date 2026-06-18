import PropTypes from "prop-types";
import { IconAlertTriangle } from "./common/icons";
import { useI18n } from "../contexts/I18nContext";

/**
 * Mandatory "this is not a real order / simulation only / not investment advice"
 * notice, shown on every VİOP and bond/bill trade surface. Pass `risk="viop"`
 * or `risk="bond"` to append the asset-specific risk explanation.
 */
export default function SimulationDisclaimer({ risk, style }) {
    const { t } = useI18n();
    return (
        <div style={{ ...s.box, ...style }} role="note">
            <div style={s.main}><IconAlertTriangle size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />{t("disclaimer.simNotReal")}</div>
            {risk === "viop" && <div style={s.risk}>{t("disclaimer.viopRisk")}</div>}
            {risk === "bond" && <div style={s.risk}>{t("disclaimer.bondRisk")}</div>}
        </div>
    );
}

SimulationDisclaimer.propTypes = {
    risk: PropTypes.oneOf(["viop", "bond"]),
    style: PropTypes.object,
};

const s = {
    box: {
        padding: "10px 14px",
        borderRadius: 8,
        background: "rgba(245, 158, 11, 0.10)",
        border: "1px solid rgba(245, 158, 11, 0.35)",
        color: "var(--text-secondary, var(--text-muted))",
        fontSize: 12,
        lineHeight: 1.5,
    },
    main: { fontWeight: 600 },
    risk: { marginTop: 6, color: "var(--text-muted)" },
};
