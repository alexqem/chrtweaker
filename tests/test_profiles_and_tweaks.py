import unittest

from mycar import config, tweaks


class ProfilesAndTweaksTest(unittest.TestCase):
    def test_unknown_profile_falls_back_to_generic(self) -> None:
        key, prof = config.resolve_profile("unknown_model_xyz")
        self.assertEqual(key, "generic")
        self.assertFalse(prof["cvt_probe"])
        self.assertFalse(prof["verified"])

    def test_set_profile_warns_on_unknown_profile(self) -> None:
        prev = config.PROFILE_NAME
        try:
            with self.assertWarns(UserWarning):
                config.set_profile("2zfe")
            self.assertEqual(config.PROFILE_NAME, "generic")
        finally:
            config.set_profile(prev)

    def test_only_8nrfts_is_verified(self) -> None:
        verified = [k for k, v in config.PROFILES.items() if v["verified"]]
        self.assertEqual(verified, ["8nrfts"])

    def test_tweaks_loaded_from_shared_json(self) -> None:
        self.assertEqual(len(tweaks.TWEAKS), 18)
        abs_spec = tweaks.TWEAK_MAP["abs_inspection"]
        self.assertEqual(abs_spec.session_cmd, "1003")
        rev = tweaks.TWEAK_MAP["reverse_beep_cluster"]
        self.assertEqual(rev.ecu, "0x7C0")
        self.assertEqual(rev.read_cmd, "21AC")
        self.assertFalse(rev.verified)
        self.assertTrue(tweaks.TWEAK_MAP["auto_fold_mirrors"].verified)

    def test_cvt_221627_decode(self) -> None:
        from mycar import toyota

        self.assertIn("221627", toyota.CVT_CMDS)
        # (0x69 * 256 + 0x00) / 256 - 40 = 105 - 40 = 65.0 C
        val, cands = toyota.decode_atf("221627", [0x69, 0x00])
        self.assertEqual(val, 65.0)
        self.assertTrue(cands)

    def test_read_all_switches_states_and_unsupported(self) -> None:
        def fake_send(cmd: str) -> str:
            if cmd.startswith("ATSH"):
                return "OK"
            if cmd == "21A7":
                return "61 A7 01"
            if cmd == "21AC":
                return "7F 21 12"
            return f"61 {cmd[2:]} 00"

        res = tweaks.read_all_switches(elm_send_fn=fake_send)
        self.assertTrue(res["states"]["auto_fold_mirrors"])
        self.assertTrue(res["supported"]["auto_fold_mirrors"])
        self.assertFalse(res["supported"]["reverse_beep_cluster"])
        self.assertFalse(res["states"]["blink_hazards_lock"])


if __name__ == "__main__":
    unittest.main()
