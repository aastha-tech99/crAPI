package com.crapi.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePhoneForm {
  @NotBlank
  @Size(max = 15)
  @Pattern(regexp = "^\\+?[0-9]+$", message = "Invalid phone number")
  private String old_number;

  @NotBlank
  @Size(max = 15)
  @Pattern(regexp = "^\\+?[0-9]+$", message = "Invalid phone number")
  private String new_number;

  @Size(min = 3, max = 4)
  private String otp;
}
