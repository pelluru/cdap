/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package co.cask.cdap.examples.purchase;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * This class represents a purchase made by a customer. It is a very simple class and only contains
 * the name of the customer, the name of the product, product quantity, price paid, and the purchase time.
 */
public class Purchase implements Writable {

  private String customer, product;
  private int quantity, price;
  private long purchaseTime;
  private String catalogId;

  public Purchase() {
  }

  public Purchase(Purchase other) {
    this(other.getCustomer(), other.getProduct(), other.getQuantity(), other.getPrice(),
         other.getPurchaseTime(), other.getCatalogId());
  }

  public Purchase(String customer, String product, int quantity, int price, long purchaseTime) {
    this(customer, product, quantity, price, purchaseTime, null);
  }

  public Purchase(String customer, String product, int quantity, int price, long purchaseTime,
                  @Nullable String catalogId) {
    this.customer = customer;
    this.product = product;
    this.quantity = quantity;
    this.price = price;
    this.purchaseTime = purchaseTime;
    this.catalogId = catalogId;
  }

  public String getCustomer() {
    return customer;
  }

  public String getProduct() {
    return product;
  }

  public long getPurchaseTime() {
    return purchaseTime;
  }

  public int getQuantity() {
    return quantity;
  }

  public int getPrice() {
    return price;
  }

  public String getCatalogId() {
    return catalogId;
  }

  public void setCatalogId(String catalogId) {
    this.catalogId = catalogId;
  }

  @Override
  public void write(DataOutput out) throws IOException {
    WritableUtils.writeString(out, customer);
    WritableUtils.writeString(out, product);
    WritableUtils.writeVInt(out, quantity);
    WritableUtils.writeVInt(out, price);
    WritableUtils.writeVLong(out, purchaseTime);
    WritableUtils.writeString(out, catalogId);
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    customer = WritableUtils.readString(in);
    product = WritableUtils.readString(in);
    quantity = WritableUtils.readVInt(in);
    price = WritableUtils.readVInt(in);
    purchaseTime = WritableUtils.readVLong(in);
    catalogId = WritableUtils.readString(in);
  }
}
