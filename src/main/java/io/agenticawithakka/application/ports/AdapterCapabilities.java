package io.agenticawithakka.application.ports;

import io.agenticawithakka.domain.contracts.ContractValidation;
import java.util.List;
import java.util.Set;

/** Every adapter declares what it supports and its known limitations. */
public record AdapterCapabilities(String adapterName, Set<String> supported, List<String> limitations) {
    public AdapterCapabilities {
        ContractValidation.matches(adapterName, "adapterName", ContractValidation.REFERENCE);
        supported = ContractValidation.set(supported, "supported", 64);
        limitations = ContractValidation.list(limitations, "limitations", 64);
        for (String limitation : limitations) {
            ContractValidation.text(limitation, "limitations", 500);
        }
    }
}
