/*
 * Radix Gateway API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 0.9.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.radixdlt.api.gateway.openapitools.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;


/**
 * TransactionRules
 */
@JsonPropertyOrder({
  TransactionRules.JSON_PROPERTY_MAXIMUM_MESSAGE_LENGTH,
  TransactionRules.JSON_PROPERTY_MINIMUM_STAKE
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-11-27T11:34:49.994520-06:00[America/Chicago]")
public class TransactionRules {
  public static final String JSON_PROPERTY_MAXIMUM_MESSAGE_LENGTH = "maximum_message_length";
  private Integer maximumMessageLength;

  public static final String JSON_PROPERTY_MINIMUM_STAKE = "minimum_stake";
  private TokenAmount minimumStake;


  public TransactionRules maximumMessageLength(Integer maximumMessageLength) {
    this.maximumMessageLength = maximumMessageLength;
    return this;
  }

   /**
   * Get maximumMessageLength
   * @return maximumMessageLength
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")
  @JsonProperty(JSON_PROPERTY_MAXIMUM_MESSAGE_LENGTH)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public Integer getMaximumMessageLength() {
    return maximumMessageLength;
  }


  @JsonProperty(JSON_PROPERTY_MAXIMUM_MESSAGE_LENGTH)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setMaximumMessageLength(Integer maximumMessageLength) {
    this.maximumMessageLength = maximumMessageLength;
  }


  public TransactionRules minimumStake(TokenAmount minimumStake) {
    this.minimumStake = minimumStake;
    return this;
  }

   /**
   * Get minimumStake
   * @return minimumStake
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "")
  @JsonProperty(JSON_PROPERTY_MINIMUM_STAKE)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)

  public TokenAmount getMinimumStake() {
    return minimumStake;
  }


  @JsonProperty(JSON_PROPERTY_MINIMUM_STAKE)
  @JsonInclude(value = JsonInclude.Include.USE_DEFAULTS)
  public void setMinimumStake(TokenAmount minimumStake) {
    this.minimumStake = minimumStake;
  }


  /**
   * Return true if this TransactionRules object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TransactionRules transactionRules = (TransactionRules) o;
    return Objects.equals(this.maximumMessageLength, transactionRules.maximumMessageLength) &&
        Objects.equals(this.minimumStake, transactionRules.minimumStake);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maximumMessageLength, minimumStake);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class TransactionRules {\n");
    sb.append("    maximumMessageLength: ").append(toIndentedString(maximumMessageLength)).append("\n");
    sb.append("    minimumStake: ").append(toIndentedString(minimumStake)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

