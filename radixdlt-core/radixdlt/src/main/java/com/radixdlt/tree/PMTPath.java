/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.tree;

import static com.radixdlt.tree.PMTPath.RemainingSubtree.EXISTING;
import static com.radixdlt.tree.PMTPath.RemainingSubtree.NEW;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

public class PMTPath {

  enum RemainingSubtree {
    EXISTING,
    NEW,
    EXISTING_AND_NEW,
    NONE
  }

  private final PMTKey commonPrefix;
  private final Map<RemainingSubtree, PMTKey> rem = new EnumMap<>(RemainingSubtree.class);
  private RemainingSubtree suffix;

  public PMTKey getRemainder(RemainingSubtree remainingSubtree) {
    if (remainingSubtree == NEW || remainingSubtree == EXISTING) {
      return rem.get(remainingSubtree);
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unexpected subtree: %s. There can be only NEW or EXISTING remainder",
              remainingSubtree));
    }
  }

  public PMTKey getCommonPrefix() {
    return commonPrefix;
  }

  // INFO: Branch has empty key and remainder
  public PMTPath(PMTKey current, PMTKey incoming, PMTKey common) {

    this.rem.put(EXISTING, current);
    this.rem.put(NEW, incoming);
    this.commonPrefix = common;
    recogniseRemainder(current, incoming);
  }

  public PMTPath recogniseRemainder(PMTKey current, PMTKey incoming) {
    // TODO remove null possibility
    if (current == null || current.isEmpty()) {
      if (incoming.isEmpty()) {
        this.suffix = RemainingSubtree.NONE;
      } else {
        this.suffix = NEW;
      }
    } else {
      if (incoming.isEmpty()) {
        this.suffix = EXISTING;
      } else {
        this.suffix = RemainingSubtree.EXISTING_AND_NEW;
      }
    }
    return this;
  }

  public RemainingSubtree whichRemainderIsLeft() {
    return this.suffix;
  }

  public static PMTPath findCommonPath(PMTKey current, PMTKey incoming) {
    // INFO: Branch key doesn't exist
    if (current == null || current.isEmpty() || incoming.isEmpty()) {
      return new PMTPath(current, incoming, new PMTKey(new byte[0]));
    } else {
      byte[] currentNibs = current.getRaw();
      byte[] incomingNibs = incoming.getRaw();

      var shorter = Math.min(currentNibs.length, incomingNibs.length);
      var longest = Math.max(currentNibs.length, incomingNibs.length);

      ByteArrayOutputStream commonElementsStream = new ByteArrayOutputStream();

      var commonLength = 0;
      for (int i = 0; i < shorter; i++) {
        if (currentNibs[i] == incomingNibs[i]) {
          commonLength += 1;
          commonElementsStream.write(currentNibs, i, 1);
        } else {
          break;
        }
      }
      byte[] commonElements = commonElementsStream.toByteArray();

      byte[] currentRem;
      if (commonLength < currentNibs.length) {
        currentRem = new byte[currentNibs.length - commonLength];
        for (int i = 0; i < currentRem.length; i++) {
          currentRem[i] = currentNibs[commonLength + i];
        }
      } else {
        currentRem = new byte[0];
      }

      byte[] incomingRem;
      if (commonLength < incomingNibs.length) {
        incomingRem = new byte[incomingNibs.length - commonLength];
        for (int i = 0; i < incomingRem.length; i++) {
          incomingRem[i] = incomingNibs[commonLength + i];
        }
      } else {
        incomingRem = new byte[0];
      }

      return new PMTPath(
          new PMTKey(currentRem), new PMTKey(incomingRem), new PMTKey(commonElements));
    }
  }

  public static byte[] intoNibbles(byte[] bytes) {
    var nibs = new byte[bytes.length * 2];
    var nibsIndex = 0;
    for (byte b : bytes) {
      byte high = (byte) ((b & 0xf0) >> 4);
      byte low = (byte) (b & 0xf);
      nibs[nibsIndex] = high;
      nibs[nibsIndex + 1] = low;
      nibsIndex = nibsIndex + 2;
    }
    return nibs;
  }
}
