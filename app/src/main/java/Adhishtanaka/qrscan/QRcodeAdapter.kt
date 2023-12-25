
import Adhishtanaka.qrscan.QRCodeData
import Adhishtanaka.qrscan.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView



class QRCodeAdapter(
    private val originalQrCodeDataList: List<QRCodeData>,
    private val onItemClick: (QRCodeData) -> Unit
) : RecyclerView.Adapter<QRCodeAdapter.ViewHolder>() {
    private var qrCodeDataList: List<QRCodeData> = originalQrCodeDataList

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewQRCode: TextView = itemView.findViewById(R.id.textViewQRCode)
        val textViewDateTime: TextView = itemView.findViewById(R.id.textViewDateTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_qr_code, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val qrCodeData = qrCodeDataList[position]
        holder.textViewQRCode.text = qrCodeData.details
        holder.textViewDateTime.text = qrCodeData.datetime

        holder.itemView.setOnClickListener {
            onItemClick(qrCodeData)
        }
    }

    override fun getItemCount(): Int {
        return qrCodeDataList.size
    }

    fun filterList(filteredList: List<QRCodeData>) {
        qrCodeDataList = filteredList
        notifyDataSetChanged()
    }
}
