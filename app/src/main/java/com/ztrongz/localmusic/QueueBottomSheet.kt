override fun onStart() {
    super.onStart()
    val dialog = dialog as? BottomSheetDialog
    dialog?.behavior?.apply {
        state = BottomSheetBehavior.STATE_EXPANDED
        skipCollapsed = true
        // Ép chiều cao của BottomSheet bằng toàn bộ màn hình
        peekHeight = android.view.ViewGroup.LayoutParams.MATCH_PARENT
    }
    dialog?.window?.setDimAmount(0.4f)
}