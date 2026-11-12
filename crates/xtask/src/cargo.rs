//! Workspace build commands must never resolve a different dependency graph.

use std::{env, process::Command};

/// Cargo's executable may be set by the caller or by the outer `cargo run`.
/// Put `--locked` after the subcommand: it is not a global Cargo option.
/// The caller's `RUSTUP_TOOLCHAIN` remains inherited by nested commands.
pub(crate) fn command(subcommand: &str) -> Command {
    let mut command = Command::new(env::var_os("CARGO").unwrap_or_else(|| "cargo".into()));
    command.args([subcommand, "--locked"]);
    command
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn nested_commands_preserve_the_workspace_lock() {
        for subcommand in ["build", "clippy", "metadata"] {
            let command = command(subcommand);
            let args: Vec<_> = command.get_args().collect();
            assert_eq!(args, [subcommand, "--locked"]);
        }
    }

    #[test]
    fn stale_runtime_workspace_lock_is_rejected_without_modification() {
        let directory = env::temp_dir().join(format!("nsh-xtask-lock-{}", std::process::id()));
        std::fs::create_dir_all(&directory).unwrap();
        let manifest = directory.join("Cargo.toml");
        let lock = directory.join("Cargo.lock");
        let original =
            "version = 4\n[[package]]\nname = \"synthetic-runtime\"\nversion = \"0.1.0\"\n";
        std::fs::write(&manifest, "[package]\nname = \"synthetic-runtime\"\nversion = \"0.2.0\"\nedition = \"2024\"\n[lib]\npath = \"lib.rs\"\n[workspace]\n").unwrap();
        std::fs::write(directory.join("lib.rs"), "").unwrap();
        std::fs::write(&lock, original).unwrap();
        let result = command("metadata")
            .args(["--offline", "--format-version", "1", "--manifest-path"])
            .arg(&manifest)
            .output()
            .unwrap();
        assert!(!result.status.success());
        assert!(String::from_utf8_lossy(&result.stderr).contains("--locked"));
        assert_eq!(std::fs::read_to_string(lock).unwrap(), original);
        std::fs::remove_dir_all(directory).unwrap();
    }
}
