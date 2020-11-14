/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "java/ClassDefinition.h"

#include "androidfw/StringPiece.h"

using ::aapt::text::Printer;
using ::android::StringPiece;

namespace aapt {

void ClassMember::Print(bool /*final*/, Printer* printer) const {
  processor_.Print(printer);
}

void MethodDefinition::AppendStatement(const StringPiece& statement) {
  statements_.push_back(statement.to_string());
}

void MethodDefinition::Print(bool final, Printer* printer) const {
  printer->Print(signature_).Println(" {");
  printer->Indent();
  for (const auto& statement : statements_) {
    printer->Println(statement);
  }
  printer->Undent();
  printer->Print("}");
}

ClassDefinition::Result ClassDefinition::AddMember(std::unique_ptr<ClassMember> member) {
  Result result = Result::kAdded;
  auto iter = indexed_members_.find(member->GetName());
  if (iter != indexed_members_.end()) {
    // Overwrite the entry. Be careful, as the key in indexed_members_ is actually memory owned
    // by the value at ordered_members_[index]. Since overwriting a value for a key doesn't replace
    // the key (the initial key inserted into the unordered_map is kept), we must erase and then
    // insert a new key, whose memory is being kept around. We do all this to avoid using more
    // memory for each key.
    size_t index = iter->second;

    // Erase the key + value from the map.
    indexed_members_.erase(iter);

    // Now clear the memory that was backing the key (now erased).
    ordered_members_[index].reset();
    result = Result::kOverridden;
  }

  indexed_members_[member->GetName()] = ordered_members_.size();
  ordered_members_.push_back(std::move(member));
  return result;
}

bool ClassDefinition::empty() const {
  for (const std::unique_ptr<ClassMember>& member : ordered_members_) {
    if (member != nullptr && !member->empty()) {
      return false;
    }
  }
  return true;
}

void ClassDefinition::Print(bool final, Printer* printer) const {
  if (empty() && !create_if_empty_) {
    return;
  }

  ClassMember::Print(final, printer);

  printer->Print("public ");
  if (qualifier_ == ClassQualifier::kStatic) {
    printer->Print("static ");
  }
  printer->Print("final class ").Print(name_).Println(" {");
  printer->Indent();

  for (const std::unique_ptr<ClassMember>& member : ordered_members_) {
    // There can be nullptr members when a member is added to the ClassDefinition
    // and takes precedence over a previous member with the same name. The overridden member is
    // set to nullptr.
    if (member != nullptr) {
      member->Print(final, printer);
      printer->Println();
    }
  }

  printer->Undent();
  printer->Print("}");
}

constexpr static const char* sWarningHeader =
    "/* AUTO-GENERATED FILE. DO NOT MODIFY.\n"
    " *\n"
    " * This class was automatically generated by the\n"
    " * aapt tool from the resource data it found. It\n"
    " * should not be modified by hand.\n"
    " */\n\n";

void ClassDefinition::WriteJavaFile(const ClassDefinition* def, const StringPiece& package,
                                    bool final, io::OutputStream* out) {
  Printer printer(out);
  printer.Print(sWarningHeader).Print("package ").Print(package).Println(";");
  printer.Println();
  def->Print(final, &printer);
}

}  // namespace aapt
