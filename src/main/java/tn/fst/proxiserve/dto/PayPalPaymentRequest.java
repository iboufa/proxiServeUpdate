package tn.fst.proxiserve.dto;

import lombok.Data;

@Data
public class PayPalPaymentRequest {
    private String amount;
    private String currency;
}
