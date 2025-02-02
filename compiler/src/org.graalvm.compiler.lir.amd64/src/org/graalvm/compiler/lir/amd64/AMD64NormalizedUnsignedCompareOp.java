/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Returns -1, 0, or 1 if either x &lt; y, x == y, or x &gt; y.
 */
// @formatter:off
@StubPort(path      = "src/hotspot/cpu/x86/x86_64.ad",
          lineStart = 12800,
          lineEnd   = 12824,
          commit    = "afda8fbf0bcea18cbe741e9c693789ebe0c6c4c5",
          sha1      = "997a3fd0c34599035d44866b15f1efc7eb28945e")
@StubPort(path      = "src/hotspot/cpu/x86/x86_64.ad",
          lineStart = 12852,
          lineEnd   = 12876,
          commit    = "afda8fbf0bcea18cbe741e9c693789ebe0c6c4c5",
          sha1      = "9c6287fe33d5c9be006582942ba131188a924ee3")
// @formatter:on
public class AMD64NormalizedUnsignedCompareOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64NormalizedUnsignedCompareOp> TYPE = LIRInstructionClass.create(AMD64NormalizedUnsignedCompareOp.class);

    @Def({REG}) protected AllocatableValue result;
    @Use({REG}) protected AllocatableValue x;
    @Use({REG}) protected AllocatableValue y;

    public AMD64NormalizedUnsignedCompareOp(AllocatableValue result, AllocatableValue x, AllocatableValue y) {
        super(TYPE);

        this.result = result;
        this.x = x;
        this.y = y;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label done = new Label();
        if (x.getPlatformKind() == AMD64Kind.DWORD) {
            masm.cmpl(asRegister(x), asRegister(y));
        } else {
            GraalError.guarantee(x.getPlatformKind() == AMD64Kind.QWORD, "unsupported value kind %s", x.getPlatformKind());
            masm.cmpq(asRegister(x), asRegister(y));
        }
        masm.movl(asRegister(result), -1);
        masm.jccb(AMD64Assembler.ConditionFlag.Below, done);
        masm.setl(AMD64Assembler.ConditionFlag.NotEqual, asRegister(result));
        masm.bind(done);
    }
}
