package com.crapi.entity;

import com.crapi.enums.EStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Entity
@Table(name = "otp_phoneNumberChange")
@Data
public class ChangePhoneRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private long id;

  @Pattern(regexp = "^\\+?[0-9]+$", message = "Invalid phone number")
  @Column(name = "new_phone")
  private String newPhone;

  @Pattern(regexp = "^\\+?[0-9]+$", message = "Invalid phone number")
  @Column(name = "old_phone")
  private String oldPhone;

  @Column(name = "otp")
  private String otp;

  private String status;

  @OneToOne private User user;

  public ChangePhoneRequest() {}

  public ChangePhoneRequest(String newPhone, String oldPhone, String otp, User user) {
    validatePhone(newPhone);
    validatePhone(oldPhone);
    this.newPhone = newPhone;
    this.oldPhone = oldPhone;
    this.otp = otp;
    this.user = user;
    this.status = EStatus.ACTIVE.toString();
  }

  private static void validatePhone(String phone) {
    if (phone == null || !phone.matches("^\\+?[0-9]+$")) {
      throw new IllegalArgumentException("Invalid phone number");
    }
  }
}
